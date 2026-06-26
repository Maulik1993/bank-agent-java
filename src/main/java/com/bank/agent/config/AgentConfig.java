package com.bank.agent.config;

import com.bank.agent.observability.ObservabilityStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class AgentConfig {

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
            .maxOutputTokens(16384)
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
}
