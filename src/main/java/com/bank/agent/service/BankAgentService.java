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
            .append("For each RECOMMENDED product:\n")
            .append("- Opening balance + new deposits = end balance\n")
            .append("- Interest earned using the annual rate from the catalogue\n")
            .append("Total interest across all recommended products. Grand total balance at year end.\n\n")
            .append("SECTION 6 — PERSONALISED ACTION PLAN\n")
            .append("3-5 concrete numbered steps tailored to THIS customer.\n")
            .append("Each step must include a specific amount, product name, and timing.\n\n")
            .append("RULES:\n")
            .append("- Use exact figures from the data. Never round to a vague estimate.\n")
            .append("- Never invent figures not present in the data.\n")
            .append("- Never use placeholder text like [X] or [amount].\n")
            .append("- Write in second person (you/your) addressing the customer directly.\n")
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


