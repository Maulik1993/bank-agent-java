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

    public static final String SYSTEM_PROMPT = """
        You are an expert banking AI advisor with access to tools.

        TASK: When a user asks about savings, investments, or financial planning:
        Step 1 - Call customerIdSearch with the customer ID.
        Step 2 - Call getCustomerProfile with the customer ID.
        Step 3 - Call analyzeSpendingBehavior with the customer ID.
        Step 4 - Call analyzeSavingsBehavior with the customer ID.
        Step 5 - Call recommendSavingsProduct with the customer ID.
        Step 6 - Call getCustomerFinancialHistory with the customer ID.
        Step 7 - Call getMatchingProducts with the customer ID and "growth".
        Step 8 - Call calculateOptimalAllocation with appropriate parameters.
        Step 9 - Call personalizeRecommendation with the customer name, ID, a recommendation summary, and next steps.

        After step 9, write your FINAL ANSWER as a complete, detailed financial report covering:
        - Financial snapshot (balances, liquidity)
        - Transactional activity and monthly surplus
        - Expense reduction suggestions
        - Recommended product allocation with interest rates
        - Projected annual savings and interest
        - Personalised action plan

        IMPORTANT: You MUST produce a detailed written response. Never return empty. Never return just "Done".

        USER REQUEST:
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
            .chatMemory(MessageWindowChatMemory.withMaxMessages(50))
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
        String chat(@UserMessage String fullMessage);
    }
}
