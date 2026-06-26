package com.bank.agent.service;

import com.bank.agent.observability.ObservabilityStore;
import com.bank.agent.tools.BehaviorAnalysisTool;
import com.bank.agent.tools.CustomerInsightsTool;
import com.bank.agent.tools.CustomerSearchTool;
import com.bank.agent.tools.OptimizationTool;
import com.bank.agent.tools.PersonalizationTool;
import com.bank.agent.tools.ProductMatchingTool;
import com.bank.agent.tools.RecommendationTool;
import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankAgentService {

    private static final ThreadLocal<String> CURRENT_SESSION_ID = new ThreadLocal<>();

    private final VertexAiGeminiChatModel chatModel;
    private final CustomerSearchTool customerSearchTool;
    private final CustomerInsightsTool customerInsightsTool;
    private final BehaviorAnalysisTool behaviorAnalysisTool;
    private final RecommendationTool recommendationTool;
    private final ProductMatchingTool productMatchingTool;
    private final OptimizationTool optimizationTool;
    private final PersonalizationTool personalizationTool;
    private final ObservabilityStore observabilityStore;

    public static String getCurrentSessionId() {
        return CURRENT_SESSION_ID.get();
    }

    public String chat(String sessionId, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "Please provide a message.";
        }
        log.info("Chat request: session={} message={}", sessionId, userMessage);
        CURRENT_SESSION_ID.set(sessionId);

        try {
            // Extract customer ID from message
            String customerId = extractCustomerId(userMessage);
            if (customerId == null) {
                return callLlmDirect(userMessage, null);
            }

            // Orchestrate all tools in sequence
            log.info("Orchestrating tools for customer: {}", customerId);

            String identity      = safeCall("customerIdSearch",          () -> customerSearchTool.customerIdSearch(customerId));
            String profile       = safeCall("getCustomerProfile",        () -> customerInsightsTool.getCustomerProfile(customerId));
            String spending      = safeCall("analyzeSpendingBehavior",   () -> behaviorAnalysisTool.analyzeSpendingBehavior(customerId));
            String savings       = safeCall("analyzeSavingsBehavior",    () -> behaviorAnalysisTool.analyzeSavingsBehavior(customerId));
            String recommendation= safeCall("recommendSavingsProduct",   () -> recommendationTool.recommendSavingsProduct(customerId));
            String history       = safeCall("getCustomerFinancialHistory",() -> customerInsightsTool.getCustomerFinancialHistory(customerId));
            String products      = safeCall("getMatchingProducts",       () -> productMatchingTool.getMatchingProducts(customerId, "growth"));
            String allocation    = safeCall("calculateOptimalAllocation", () -> optimizationTool.calculateOptimalAllocation("moderate", "weekly", 2000.0, 12));

            // Build full context prompt for LLM — pass product catalogue separately
            String contextPrompt = buildContextPrompt(userMessage, customerId,
                identity, profile, spending, savings, recommendation, history, products, allocation);

            String llmResponse = callLlmDirect(contextPrompt, sessionId);

            // Wrap in personalized message
            String finalResponse = safeCall("personalizeRecommendation",
                () -> personalizationTool.personalizeRecommendation(
                    extractName(profile), customerId, llmResponse, "See above for your action plan."));

            log.info("Chat complete: session={} responseLength={}", sessionId,
                finalResponse == null ? 0 : finalResponse.length());
            return (finalResponse == null || finalResponse.isBlank()) ? llmResponse : finalResponse;

        } catch (Exception e) {
            log.error("Chat error: session={}", sessionId, e);
            return "An error occurred processing your request: " + e.getMessage();
        } finally {
            CURRENT_SESSION_ID.remove();
        }
    }

    private String callLlmDirect(String prompt, String sessionId) {
        log.debug("=== LLM PROMPT (session={}) ===\n{}\n=== END PROMPT ===", sessionId, prompt);
        log.info("Calling LLM: session={} promptLength={} chars", sessionId, prompt.length());
        try {
            String response = chatModel.generate(prompt);
            log.info("LLM response received: session={} responseLength={} chars", sessionId,
                response == null ? 0 : response.length());
            log.debug("=== LLM RESPONSE (session={}) ===\n{}\n=== END RESPONSE ===", sessionId, response);
            return response == null ? "" : response;
        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage(), e);
            return "LLM error: " + e.getMessage();
        }
    }

    private String safeCall(String toolName, java.util.function.Supplier<String> fn) {
        long start = System.currentTimeMillis();
        try {
            String result = fn.get();
            long ms = System.currentTimeMillis() - start;
            log.info("[TOOL] {} -> {}ms, {} chars", toolName, ms, result == null ? 0 : result.length());
            log.debug("[TOOL] {} result:\n{}", toolName, result);
            return result == null ? "" : result;
        } catch (Exception e) {
            log.warn("[TOOL] {} FAILED after {}ms: {}", toolName, System.currentTimeMillis() - start, e.getMessage());
            return "unavailable";
        }
    }

    private String buildContextPrompt(String userRequest, String customerId,
        String identity, String profile, String spending, String savings,
        String recommendation, String history, String products, String allocation) {

        // Use StringBuilder instead of String.formatted() — tool results contain
        // '%' characters (e.g. "3.50%") that would be misinterpreted as format specifiers
        return new StringBuilder()
            .append("You are a senior banking advisor AI. Using ONLY the customer data below, write a personalised financial recommendation report.\n\n")
            .append("USER REQUEST: ").append(userRequest).append("\n\n")
            .append("=== CUSTOMER DATA ===\n")
            .append("[1] Identity:           ").append(identity).append("\n")
            .append("[2] Profile:            ").append(profile).append("\n")
            .append("[3] Spending Analysis:  ").append(spending).append("\n")
            .append("[4] Savings Analysis:   ").append(savings).append("\n")
            .append("[5] Financial Data (JSON - cash flow, balances, top expenses, available products):\n").append(recommendation).append("\n")
            .append("[6] Financial History:  ").append(history).append("\n")
            .append("[7] Current Accounts:   ").append(products).append("\n")
            .append("[8] Allocation Hint:    ").append(allocation).append("\n\n")
            .append("=== YOUR TASK ===\n")
            .append("Write a COMPREHENSIVE personalised report with ALL 6 sections below.\n\n")
            .append("SECTION 1 — FINANCIAL SNAPSHOT\n")
            .append("State exact total balance, each account balance and %, liquidity ratio, monthly income, monthly spend, monthly surplus.\n\n")
            .append("SECTION 2 — TRANSACTIONAL ACTIVITY\n")
            .append("Analyse top income sources and top expenses. State the monthly surplus exactly (income - spend).\n")
            .append("Comment on whether the spending pattern is healthy or concerning.\n\n")
            .append("SECTION 3 — EXPENSE REDUCTION SUGGESTIONS\n")
            .append("For each discretionary expense in the data, suggest a realistic reduction.\n")
            .append("Calculate monthly saving and annual saving for each suggestion.\n")
            .append("Give a total potential monthly and annual saving figure.\n\n")
            .append("SECTION 4 — DYNAMIC PRODUCT RECOMMENDATION\n")
            .append("THIS IS THE MOST IMPORTANT SECTION. Do NOT just default to Current/Savings/ISA.\n")
            .append("The availableProducts list is in the JSON under [5]. For EACH product:\n")
            .append("- State whether it is RECOMMENDED, OPTIONAL, or NOT SUITABLE for this customer\n")
            .append("- Give a specific reason based on: age, income, monthly surplus, tax situation, risk, time horizon\n")
            .append("- For RECOMMENDED products, state the exact monthly amount to allocate and projected annual interest\n")
            .append("- Check eligibility carefully (e.g. LISA is age 18-39 ONLY, Stocks ISA needs 5+ year horizon)\n")
            .append("Show your reasoning clearly. Let the customer profile drive the selection.\n\n")
            .append("SECTION 5 — PROJECTED ANNUAL RETURNS\n")
            .append("Print a table with EXACTLY these columns: Product | Opening Balance | Monthly Deposit | Total Deposited (12 months) | Annual Rate | Interest Earned | Year-End Balance\n")
            .append("Rules for the table:\n")
            .append("  - Use opening balance from Section 1 for each existing account\n")
            .append("  - Monthly deposit = the allocation figure you assigned in Section 4\n")
            .append("  - Total Deposited = monthly deposit x 12\n")
            .append("  - Interest Earned = (Opening Balance + Total Deposited) x (Annual Rate / 100), rounded to 2 decimal places\n")
            .append("  - Year-End Balance = Opening Balance + Total Deposited + Interest Earned\n")
            .append("  - Only include RECOMMENDED products (skip OPTIONAL and NOT SUITABLE)\n")
            .append("After the table, print two summary lines:\n")
            .append("  TOTAL INTEREST EARNED THIS YEAR: GBP [sum of all interest earned]\n")
            .append("  TOTAL PROJECTED BALANCE AT YEAR END: GBP [sum of all year-end balances]\n")
            .append("Do not add any narrative before completing the full table and summary lines.\n\n")
            .append("SECTION 6 — PERSONALISED ACTION PLAN\n")
            .append("Write exactly 5 numbered steps. Every step MUST:\n")
            .append("  a) Reference a SPECIFIC figure already computed in Sections 1-5 (exact GBP amount or rate)\n")
            .append("  b) Name the exact product to act on\n")
            .append("  c) State a concrete timing: 'this week', 'by end of this month', 'monthly from now', etc.\n")
            .append("  d) State the expected outcome (e.g. projected interest earned, balance after 12 months)\n\n")
            .append("Required step topics (cover all 5):\n")
            .append("  Step 1 — Emergency buffer: Is the current liquid balance sufficient for 3 months of spending? State the shortfall or surplus in GBP.\n")
            .append("  Step 2 — Implement the top expense cut from Section 3: name the exact expense, the monthly saving, and which product to redirect that saving into.\n")
            .append("  Step 3 — Open or maximise the PRIMARY recommended product from Section 4: state opening deposit, monthly contribution, and 12-month projected balance.\n")
            .append("  Step 4 — Open or maximise the SECONDARY recommended product from Section 4 (if any): same format.\n")
            .append("  Step 5 — 12-month savings goal: state the total projected balance across all recommended products at year end (from Section 5) and the total interest earned.\n\n")
            .append("RULES:\n")
            .append("- Use exact GBP figures from the data. Never round to a vague estimate.\n")
            .append("- Never invent figures not present in the data.\n")
            .append("- Never use placeholder text like [X] or [amount] or [product name].\n")
            .append("- Write in second person (you/your) addressing the customer directly.\n")
            .append("- If a step has no applicable product (e.g. only one recommended product), combine Steps 3 and 4 and add a risk/diversification observation instead.\n")
            .toString();
    }

    private String extractCustomerId(String message) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\\bC\\d{3,4}\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(message);
        return m.find() ? m.group().toUpperCase() : null;
    }

    private String extractName(String profile) {
        if (profile == null) return "Customer";
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("name[=:\\s]+([A-Za-z ]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(profile);
        return m.find() ? m.group(1).trim() : "Customer";
    }
}


