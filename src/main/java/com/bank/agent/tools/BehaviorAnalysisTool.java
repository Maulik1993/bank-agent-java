package com.bank.agent.tools;

import com.bank.agent.observability.TracedTool;
import com.bank.agent.service.DatabaseService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BehaviorAnalysisTool {

    private final DatabaseService databaseService;

    @Tool("Analyze spending behavior: transaction count, credits/debits, avg transaction size, credit-to-debit ratio.")
    @TracedTool
    public String analyzeSpendingBehavior(String customerId) {
        return databaseService.analyzeSpending(customerId);
    }

    @Tool("Analyze savings behavior: savings balance, liquid balance, total assets, savings mix percentage.")
    @TracedTool
    public String analyzeSavingsBehavior(String customerId) {
        return databaseService.analyzeSavings(customerId);
    }
}
