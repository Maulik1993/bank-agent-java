package com.bank.agent.tools;

import com.bank.agent.observability.TracedTool;
import com.bank.agent.service.DatabaseService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductMatchingTool {

    private final DatabaseService databaseService;

    @Tool("Rank customer's existing account types by liquidity and balance.")
    @TracedTool
    public String getMatchingProducts(String customerId, String preference) {
        return databaseService.getMatchingProducts(customerId, preference);
    }
}
