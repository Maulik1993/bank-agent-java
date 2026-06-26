package com.bank.agent.tools;

import com.bank.agent.model.CustomerProfile;
import com.bank.agent.observability.TracedTool;
import com.bank.agent.service.DatabaseService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerSearchTool {

    private final DatabaseService databaseService;

    @Tool("Verify customer identity by customer ID. Always call this first.")
    @TracedTool
    public String customerIdSearch(String customerId) {
        CustomerProfile profile = databaseService.loadProfile(customerId);
        if (profile == null) {
            return "Customer verification failed: no customer found for ID " + customerId;
        }
        return "Customer verified: customerId=%s, name=%s".formatted(profile.getCustomerId(), profile.getName());
    }

    @Tool("Get detailed customer profile dump including all transactions.")
    @TracedTool
    public String customerDatabaseSearch(String customerId) {
        return databaseService.getCustomerDatabaseDump(customerId);
    }
}
