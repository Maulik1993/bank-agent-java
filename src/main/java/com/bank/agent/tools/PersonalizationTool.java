package com.bank.agent.tools;

import com.bank.agent.observability.TracedTool;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class PersonalizationTool {

    @Tool("Wrap the final recommendation in a customer-friendly personalised message. Call this last and return its output verbatim.")
    @TracedTool
    public String personalizeRecommendation(
        String customerName,
        String customerId,
        String recommendationSummary,
        String nextSteps) {
        return "Hi " + customerName + ",\n\n"
            + "Customer ID: " + customerId + "\n\n"
            + recommendationSummary + "\n\n"
            + "Next steps:\n" + nextSteps;
    }
}
