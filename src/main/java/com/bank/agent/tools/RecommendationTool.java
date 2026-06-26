package com.bank.agent.tools;

import com.bank.agent.model.CustomerProfile;
import com.bank.agent.observability.TracedTool;
import com.bank.agent.service.DatabaseService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RecommendationTool {

    private final DatabaseService databaseService;

    @Value("${app.savings-rate}")
    private double savingsRate;

    @Value("${app.isa-rate}")
    private double isaRate;

    @Tool("Get structured financial data for savings recommendation: balances, cash flow, top expenses/income, available products with interest rates.")
    @TracedTool
    public Map<String, Object> recommendSavingsProduct(String customerId) {
        CustomerProfile profile = databaseService.loadProfile(customerId);
        if (profile == null) {
            return Map.of("error", "Customer not found", "customerId", customerId);
        }

        double monthlySurplus = profile.getMonthlyIncome() - profile.getMonthlySpend();
        double liquidityRatioPct = profile.getTotalBalance() == 0.0 ? 0.0 : (profile.getCurrentBalance() * 100.0) / profile.getTotalBalance();
        double savingsRatioPct = profile.getTotalBalance() == 0.0 ? 0.0 : (profile.getSavingsBalance() * 100.0) / profile.getTotalBalance();
        double potentialMonthlyCut = profile.getMonthlySpend() * 0.10;
        double potentialAnnualCut = potentialMonthlyCut * 12;

        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("customerId", profile.getCustomerId());
        customer.put("name", profile.getName());
        customer.put("age", profile.getAge());

        Map<String, Object> balances = new LinkedHashMap<>();
        balances.put("totalBalance", profile.getTotalBalance());
        balances.put("currentAccountBalance", profile.getCurrentBalance());
        balances.put("savingsIsaBalance", profile.getSavingsBalance());
        balances.put("liquidityRatioPct", round(liquidityRatioPct));
        balances.put("savingsRatioPct", round(savingsRatioPct));

        Map<String, Object> cashFlow = new LinkedHashMap<>();
        cashFlow.put("transactionsAnalyzed", profile.getTransactionCount());
        cashFlow.put("monthlyIncomeEstimate", profile.getMonthlyIncome());
        cashFlow.put("monthlySpendingEstimate", profile.getMonthlySpend());
        cashFlow.put("monthlySurplus", round(monthlySurplus));
        cashFlow.put("totalInflows", profile.getInflows());
        cashFlow.put("totalOutflows", profile.getOutflows());

        Map<String, Object> spendingBreakdown = new LinkedHashMap<>();
        spendingBreakdown.put("topExpenses", toNamedAmounts(profile.getTopDebits()));
        spendingBreakdown.put("topIncomeSources", toNamedAmounts(profile.getTopCredits()));
        spendingBreakdown.put("potential10PctExpenseCutMonthly", round(potentialMonthlyCut));
        spendingBreakdown.put("potential10PctExpenseCutAnnual", round(potentialAnnualCut));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customer", customer);
        result.put("balances", balances);
        result.put("cashFlow", cashFlow);
        result.put("spendingBreakdown", spendingBreakdown);
        result.put("availableProducts", databaseService.getAvailableProducts(savingsRate, isaRate));
        result.put("guidanceForLlm", String.format(Locale.US,
            "Use only these figures. Prioritize emergency liquidity, compare Current Account, Savings Account (%.2f%%), and Cash ISA (%.2f%%), and propose a concrete monthly savings allocation with rationale.",
            savingsRate * 100.0,
            isaRate * 100.0));
        return result;
    }

    private List<Map<String, Object>> toNamedAmounts(List<String[]> rows) {
        return rows == null ? List.of() : rows.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("description", row[0]);
            item.put("amount", Double.parseDouble(row[1]));
            return item;
        }).toList();
    }

    private double round(double value) {
        return Double.parseDouble(String.format(Locale.US, "%.2f", value));
    }
}
