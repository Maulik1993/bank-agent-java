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
import dev.langchain4j.service.SystemMessage;
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

        // LangChain4j 0.36.2 does not support "global" as a location.
        // Fall back to us-central1 if global is configured (Python/ADK-style).
        String resolvedLocation = "global".equalsIgnoreCase(location) ? "us-central1" : location;

        return VertexAiGeminiChatModel.builder()
            .project(project)
            .location(resolvedLocation)
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
        @SystemMessage("""
            You are an expert banking advisor. Give deeply personalised, data-driven financial recommendations.

            MANDATORY TOOL PIPELINE - call ALL tools in order for savings queries:
            1. customerIdSearch(customerId)
            2. getCustomerProfile(customerId)
            3. analyzeSpendingBehavior(customerId)
            4. analyzeSavingsBehavior(customerId)
            5. recommendSavingsProduct(customerId)
            6. getCustomerFinancialHistory(customerId)
            7. getMatchingProducts(customerId, preference)
            8. calculateOptimalAllocation(riskTolerance, accessibilityRequirement, monthlySavings, horizonMonths)
            9. personalizeRecommendation(customerName, customerId, recommendationSummary, nextSteps)

            After personalizeRecommendation returns, output its result verbatim as your final response.
            Every figure must come from the tools. Never fabricate numbers.
            Never reply with just 'Done' or an empty response.
            """)
        String chat(@MemoryId String sessionId, @UserMessage String userMessage);
    }
}
