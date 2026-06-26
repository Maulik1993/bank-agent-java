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

            // Build full context prompt for LLM
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
        log.info("Calling LLM directly: session={} promptLength={}", sessionId, prompt.length());
        try {
            String response = chatModel.generate(prompt);
            log.info("LLM response: session={} responseLength={}", sessionId,
                response == null ? 0 : response.length());
            return response == null ? "" : response;
        } catch (Exception e) {
            log.error("LLM call failed", e);
            return "LLM error: " + e.getMessage();
        }
    }

    private String safeCall(String toolName, java.util.function.Supplier<String> fn) {
        try {
            String result = fn.get();
            log.info("Tool {} returned {} chars", toolName, result == null ? 0 : result.length());
            return result == null ? "" : result;
        } catch (Exception e) {
            log.warn("Tool {} failed: {}", toolName, e.getMessage());
            return "unavailable";
        }
    }

    private String buildContextPrompt(String userRequest, String customerId,
        String identity, String profile, String spending, String savings,
        String recommendation, String history, String products, String allocation) {

        return """
            You are an expert banking AI advisor. Using ONLY the data below, write a comprehensive, personalised financial recommendation report.

            USER REQUEST: %s

            === TOOL DATA ===
            [1] Customer Identity: %s
            [2] Customer Profile: %s
            [3] Spending Analysis: %s
            [4] Savings Analysis: %s
            [5] Savings Product Recommendation (JSON): %s
            [6] Financial History: %s
            [7] Matching Products: %s
            [8] Optimal Allocation: %s

            === YOUR TASK ===
            Write a DETAILED report with ALL of these sections:
            1. Financial Snapshot - total balance, account breakdown, liquidity ratio
            2. Transactional Activity - income, spending, monthly surplus
            3. Expense Reduction Suggestions - identify top expenses, suggest cuts
            4. Recommended Product Allocation - with interest rates and amounts
            5. Projected Annual Returns - interest earned, year-end balance
            6. Personalised Action Plan - concrete next steps

            Use exact figures from the data above. Be specific with amounts and percentages.
            Do NOT use placeholder text. Write the complete report now.
            """.formatted(userRequest, identity, profile, spending, savings,
                recommendation, history, products, allocation);
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


