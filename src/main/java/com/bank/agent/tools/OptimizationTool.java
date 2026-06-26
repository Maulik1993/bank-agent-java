package com.bank.agent.tools;

import com.bank.agent.observability.TracedTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OptimizationTool {

    private final ObjectMapper objectMapper;

    @Tool("Calculate optimal allocation split across Current Account, Savings Account, and ISA based on risk tolerance, accessibility requirements, and monthly savings amount.")
    @TracedTool
    public String calculateOptimalAllocation(
        String riskTolerance,
        String accessibilityRequirement,
        double monthlySavings,
        int investmentHorizonMonths) {

        int current = 35;
        int savings = 45;
        int isa = 20;

        String access = accessibilityRequirement == null ? "" : accessibilityRequirement.toLowerCase(Locale.ROOT);
        String risk = riskTolerance == null ? "" : riskTolerance.toLowerCase(Locale.ROOT);

        if (access.contains("daily") || access.contains("anytime") || access.contains("instant")) {
            current = 50;
            savings = 35;
            isa = 15;
        } else if (access.contains("weekly")) {
            current = 35;
            savings = 45;
            isa = 20;
        } else if (access.contains("monthly")) {
            current = 20;
            savings = 50;
            isa = 30;
        }

        if (risk.contains("conservative")) {
            current += 5;
            isa -= 5;
        } else if (risk.contains("aggressive")) {
            current -= 5;
            isa += 5;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("riskTolerance", riskTolerance);
        payload.put("accessibilityRequirement", accessibilityRequirement);
        payload.put("monthlySavings", monthlySavings);
        payload.put("investmentHorizonMonths", investmentHorizonMonths);
        payload.put("allocationPct", Map.of(
            "currentAccount", current,
            "savingsAccount", savings,
            "isa", isa
        ));
        payload.put("allocationAmounts", Map.of(
            "currentAccount", round(monthlySavings * current / 100.0),
            "savingsAccount", round(monthlySavings * savings / 100.0),
            "isa", round(monthlySavings * isa / 100.0)
        ));

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize allocation payload", ex);
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
