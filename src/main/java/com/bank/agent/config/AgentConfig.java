package com.bank.agent.config;

import com.bank.agent.observability.ObservabilityStore;
import com.bank.agent.tools.BehaviorAnalysisTool;
import com.bank.agent.tools.CustomerInsightsTool;
import com.bank.agent.tools.CustomerSearchTool;
import com.bank.agent.tools.OptimizationTool;
import com.bank.agent.tools.PersonalizationTool;
import com.bank.agent.tools.ProductMatchingTool;
import com.bank.agent.tools.RecommendationTool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class AgentConfig {

    private static final String SYSTEM_PROMPT = """
        You are an expert banking advisor. Your job is to give deeply personalised, data-driven financial recommendations.

        MANDATORY TOOL PIPELINE - call ALL tools in order for savings queries:
        1. customer_id_search(customerId)
        2. get_customer_profile(customerId)
        3. analyze_spending_behavior(customerId)
        4. analyze_savings_behavior(customerId)
        5. recommend_savings_product(customerId)
        6. get_customer_financial_history(customerId)
        7. get_matching_products(customerId, preference)
        8. calculate_optimal_allocation(riskTolerance, accessibilityRequirement, monthlySavings, horizonMonths)
        9. personalize_recommendation(customerName, customerId, recommendationSummary, nextSteps)

        After personalize_recommendation returns, output its result verbatim as your final response.
        Every figure must come from the tools. Never fabricate numbers.
        """;

    private final CustomerSearchTool customerSearchTool;
    private final CustomerInsightsTool customerInsightsTool;
    private final BehaviorAnalysisTool behaviorAnalysisTool;
    private final RecommendationTool recommendationTool;
    private final ProductMatchingTool productMatchingTool;
    private final OptimizationTool optimizationTool;
    private final PersonalizationTool personalizationTool;
    private final ObservabilityStore observabilityStore;

    @Bean
    VertexAiGeminiChatModel chatModel(
        @Value("${app.google-cloud-project}") String project,
        @Value("${app.google-cloud-location}") String location,
        @Value("${app.gemini-model}") String modelName) {

        // "global" is a virtual location — the actual gRPC endpoint is the non-regional
        // aiplatform.googleapis.com:443. Regional locations use {location}-aiplatform.googleapis.com.
        String endpoint = "global".equalsIgnoreCase(location)
            ? "aiplatform.googleapis.com:443"
            : location + "-aiplatform.googleapis.com:443";

        return VertexAiGeminiChatModel.builder()
            .project(project)
            .location(location)
            .endpoint(endpoint)
            .modelName(modelName)
            .maxOutputTokens(8192)
            .build();
    }

    @Bean
    BankingAssistant bankingAssistant(VertexAiGeminiChatModel chatModel) {
        return AiServices.builder(BankingAssistant.class)
            .chatLanguageModel(chatModel)
            .tools(
                customerSearchTool,
                customerInsightsTool,
                behaviorAnalysisTool,
                recommendationTool,
                productMatchingTool,
                optimizationTool,
                personalizationTool
            )
            .systemMessageProvider(memoryId -> SYSTEM_PROMPT)
            .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(30))
            .build();
    }

    @PostConstruct
    public void registerTools() {
        observabilityStore.registerTools(List.of(
            "customerIdSearch",
            "customerDatabaseSearch",
            "getCustomerProfile",
            "getCustomerFinancialHistory",
            "analyzeSpendingBehavior",
            "analyzeSavingsBehavior",
            "recommendSavingsProduct",
            "getMatchingProducts",
            "calculateOptimalAllocation",
            "personalizeRecommendation"
        ));
    }

    public interface BankingAssistant {
        String chat(@MemoryId String sessionId, @UserMessage String userMessage);
    }
}
