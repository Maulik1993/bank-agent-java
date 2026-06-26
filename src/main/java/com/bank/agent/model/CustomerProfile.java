package com.bank.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CustomerProfile {
    private String customerId;
    private String name;
    private Integer age;
    private double totalBalance;
    private double savingsBalance;
    private double currentBalance;
    private int transactionCount;
    private double inflows;
    private double outflows;
    private double monthlySpend;
    private double monthlyIncome;
    private List<String[]> topDebits;
    private List<String[]> topCredits;
}
