package com.bank.agent.tools;

import com.bank.agent.observability.TracedTool;
import com.bank.agent.service.DatabaseService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerInsightsTool {

    private final DatabaseService databaseService;

    @Tool("Get customer profile: name, age, gender, account types, total balance.")
    @TracedTool
    public String getCustomerProfile(String customerId) {
        return databaseService.getCustomerProfileSummary(customerId);
    }

    @Tool("Get customer financial history: total inflows, outflows, net cash flow, transaction count.")
    @TracedTool
    public String getCustomerFinancialHistory(String customerId) {
        return databaseService.getFinancialHistory(customerId);
    }
}
