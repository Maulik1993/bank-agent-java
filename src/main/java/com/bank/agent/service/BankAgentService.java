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

        return """
            You are a senior banking advisor AI. Using ONLY the customer data below, write a personalised financial recommendation report.

            USER REQUEST: %s

            === CUSTOMER DATA ===
            [1] Identity:           %s
            [2] Profile:            %s
            [3] Spending Analysis:  %s
            [4] Savings Analysis:   %s
            [5] Financial Data (JSON - cash flow, balances, top expenses): %s
            [6] Financial History:  %s
            [7] Current Accounts:   %s
            [8] Allocation Hint:    %s

            === AVAILABLE PRODUCTS CATALOGUE ===
            %s

            === YOUR TASK ===
            Write a COMPREHENSIVE report with ALL 6 sections below.

            SECTION 1 — FINANCIAL SNAPSHOT
            State exact total balance, each account balance and %, liquidity ratio, monthly income, monthly spend, monthly surplus.

            SECTION 2 — TRANSACTIONAL ACTIVITY
            Analyse top income sources and top expenses. State the monthly surplus exactly (income - spend).
            Comment on whether the spending pattern is healthy or concerning.

            SECTION 3 — EXPENSE REDUCTION SUGGESTIONS
            For each discretionary expense in the data, suggest a realistic % reduction.
            Calculate monthly saving and annual saving for each suggestion.
            Give a total potential monthly and annual saving figure.

            SECTION 4 — DYNAMIC PRODUCT RECOMMENDATION
            THIS IS THE MOST IMPORTANT SECTION. Do NOT just list Current/Savings/ISA by default.
            Instead, reason through the FULL product catalogue above and for EACH product:
            - State whether it is RECOMMENDED, OPTIONAL, or NOT SUITABLE for this customer
            - Give a specific reason based on: age, income, monthly surplus, tax situation, risk profile, time horizon
            - For RECOMMENDED products, state the exact monthly amount to allocate and projected annual interest
            - Check eligibility criteria carefully (e.g. LISA age limit 18-39, Stocks ISA only for 5+ year horizon)
            Show your reasoning clearly. The recommendation must be driven by the customer's actual data, not a template.

            SECTION 5 — PROJECTED ANNUAL RETURNS
            For each RECOMMENDED product, calculate:
            - Opening balance + new deposits this year = end balance
            - Interest earned (use the annual rate from the catalogue)
            Total interest across all recommended products.
            Grand total balance at year end.

            SECTION 6 — PERSONALISED ACTION PLAN
            3-5 concrete numbered steps tailored to THIS customer.
            Each step should include a specific amount, product name, and timing.

            RULES:
            - Use exact £ figures from the data. Never round to a vague estimate.
            - Never invent figures not present in the data.
            - Never use placeholder text like [X] or [amount].
            - Write in second person ("you", "your") addressing the customer directly.
            """.formatted(userRequest, identity, profile, spending, savings,
                recommendation, history, products, allocation,
                recommendation.contains("availableProducts") ? recommendation : products);
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


