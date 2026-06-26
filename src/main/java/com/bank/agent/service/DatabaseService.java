package com.bank.agent.service;

import com.bank.agent.model.CustomerProfile;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.TableResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class DatabaseService {

    private static final String SQLITE_URL = "jdbc:sqlite:bank_data.db";

    private final String bqDataset;
    private final String googleCloudProject;

    public DatabaseService(@Value("${app.bq-dataset:}") String bqDataset,
                           @Value("${app.google-cloud-project:}") String googleCloudProject) {
        this.bqDataset = bqDataset;
        this.googleCloudProject = googleCloudProject;
    }

    public CustomerProfile loadProfile(String customerId) {
        if (shouldUseBigQuery()) {
            try {
                return loadProfileFromBigQuery(customerId);
            } catch (Exception ex) {
                log.warn("BigQuery profile lookup failed, falling back to SQLite: {}", ex.getMessage());
            }
        }
        return loadProfileFromSqlite(customerId);
    }

    public String getCustomerProfileSummary(String customerId) {
        if (shouldUseBigQuery()) {
            try {
                return getCustomerProfileSummaryBigQuery(customerId);
            } catch (Exception ex) {
                log.warn("BigQuery customer summary failed, falling back to SQLite: {}", ex.getMessage());
            }
        }
        return getCustomerProfileSummarySqlite(customerId);
    }

    public String analyzeSpending(String customerId) {
        CustomerProfile profile = loadProfile(customerId);
        if (profile == null) {
            return "Customer not found for spending analysis.";
        }

        double avgTransactionSize = profile.getTransactionCount() == 0
            ? 0.0
            : (profile.getInflows() + profile.getOutflows()) / profile.getTransactionCount();
        double creditToDebitRatio = profile.getOutflows() == 0.0
            ? profile.getInflows()
            : profile.getInflows() / profile.getOutflows();

        return String.format(Locale.US,
            "Spending analysis for %s: transactionCount=%d, totalCredits=%.2f, totalDebits=%.2f, avgTransactionSize=%.2f, creditToDebitRatio=%.2f",
            customerId,
            profile.getTransactionCount(),
            profile.getInflows(),
            profile.getOutflows(),
            avgTransactionSize,
            creditToDebitRatio);
    }

    public String analyzeSavings(String customerId) {
        CustomerProfile profile = loadProfile(customerId);
        if (profile == null) {
            return "Customer not found for savings analysis.";
        }

        double totalAssets = profile.getTotalBalance();
        double savingsMixPct = totalAssets == 0.0 ? 0.0 : (profile.getSavingsBalance() * 100.0) / totalAssets;

        return String.format(Locale.US,
            "Savings analysis for %s: savingsLikeBalance=%.2f, liquidBalance=%.2f, totalAssets=%.2f, savingsMixPct=%.2f",
            customerId,
            profile.getSavingsBalance(),
            profile.getCurrentBalance(),
            totalAssets,
            savingsMixPct);
    }

    public String getFinancialHistory(String customerId) {
        CustomerProfile profile = loadProfile(customerId);
        if (profile == null) {
            return "Customer not found for financial history.";
        }

        double netCashFlow = profile.getInflows() - profile.getOutflows();
        return String.format(Locale.US,
            "Financial history for %s: inflows=%.2f, outflows=%.2f, netCashFlow=%.2f, transactionCount=%d",
            customerId,
            profile.getInflows(),
            profile.getOutflows(),
            netCashFlow,
            profile.getTransactionCount());
    }

    public String getMatchingProducts(String customerId, String preference) {
        if (shouldUseBigQuery()) {
            try {
                return getMatchingProductsBigQuery(customerId, preference);
            } catch (Exception ex) {
                log.warn("BigQuery product match failed, falling back to SQLite: {}", ex.getMessage());
            }
        }
        return getMatchingProductsSqlite(customerId, preference);
    }

    public String getCustomerDatabaseDump(String customerId) {
        CustomerProfile profile = loadProfile(customerId);
        if (profile == null) {
            return "Customer not found.";
        }

        return String.join("\n",
            getCustomerProfileSummary(customerId),
            analyzeSpending(customerId),
            analyzeSavings(customerId),
            getFinancialHistory(customerId),
            "Top debits: " + formatPairs(profile.getTopDebits()),
            "Top credits: " + formatPairs(profile.getTopCredits()));
    }

    public List<Map<String, Object>> getAvailableProducts(double savingsRate, double isaRate) {
        List<Map<String, Object>> products = new ArrayList<>();
        products.add(product("Current Account", 0.0, "instant", false));
        products.add(product("Savings Account", savingsRate * 100.0, "same-day", false));
        products.add(product("Cash ISA", isaRate * 100.0, "next-day", true));
        return products;
    }

    private CustomerProfile loadProfileFromSqlite(String customerId) {
        String sql = """
            WITH acct AS (
                SELECT customer_id,
                    ROUND(SUM(balance), 2) AS total_balance,
                    ROUND(SUM(CASE WHEN LOWER(product_type) LIKE '%savings%' OR LOWER(product_type) LIKE '%isa%' THEN balance ELSE 0 END), 2) AS savings_balance,
                    ROUND(SUM(CASE WHEN LOWER(product_type) LIKE '%current%' THEN balance ELSE 0 END), 2) AS current_balance
                FROM accounts WHERE customer_id = ? GROUP BY customer_id
            ),
            txn AS (
                SELECT a.customer_id, COUNT(t.account_id) AS transaction_count,
                    ROUND(SUM(CASE WHEN t.amount > 0 THEN t.amount ELSE 0 END), 2) AS inflows,
                    ROUND(ABS(SUM(CASE WHEN t.amount < 0 THEN t.amount ELSE 0 END)), 2) AS outflows,
                    ROUND(AVG(CASE WHEN t.amount < 0 THEN ABS(t.amount) END), 2) AS monthly_spend,
                    ROUND(AVG(CASE WHEN t.amount > 0 THEN t.amount END), 2) AS monthly_income
                FROM accounts a LEFT JOIN transactions t ON a.account_id = t.account_id
                WHERE a.customer_id = ? GROUP BY a.customer_id
            )
            SELECT c.customer_id, c.name, c.age,
                COALESCE(acct.total_balance,0) AS total_balance,
                COALESCE(acct.savings_balance,0) AS savings_balance,
                COALESCE(acct.current_balance,0) AS current_balance,
                COALESCE(txn.transaction_count,0) AS transaction_count,
                COALESCE(txn.inflows,0) AS inflows,
                COALESCE(txn.outflows,0) AS outflows,
                COALESCE(txn.monthly_spend,0) AS monthly_spend,
                COALESCE(txn.monthly_income,0) AS monthly_income
            FROM customers c
            LEFT JOIN acct ON c.customer_id = acct.customer_id
            LEFT JOIN txn ON c.customer_id = txn.customer_id
            WHERE c.customer_id = ? LIMIT 1
            """;

        try (Connection connection = DriverManager.getConnection(SQLITE_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, customerId);
            statement.setString(2, customerId);
            statement.setString(3, customerId);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Object ageObject = rs.getObject("age");
                return CustomerProfile.builder()
                    .customerId(rs.getString("customer_id"))
                    .name(rs.getString("name"))
                    .age(ageObject == null ? null : ((Number) ageObject).intValue())
                    .totalBalance(rs.getDouble("total_balance"))
                    .savingsBalance(rs.getDouble("savings_balance"))
                    .currentBalance(rs.getDouble("current_balance"))
                    .transactionCount(rs.getInt("transaction_count"))
                    .inflows(rs.getDouble("inflows"))
                    .outflows(rs.getDouble("outflows"))
                    .monthlySpend(rs.getDouble("monthly_spend"))
                    .monthlyIncome(rs.getDouble("monthly_income"))
                    .topDebits(loadTopTransactionsSqlite(customerId, false))
                    .topCredits(loadTopTransactionsSqlite(customerId, true))
                    .build();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("SQLite profile query failed", ex);
        }
    }

    private String getCustomerProfileSummarySqlite(String customerId) {
        String sql = """
            SELECT c.customer_id, c.name, c.age, c.gender,
                   GROUP_CONCAT(DISTINCT a.product_type) AS account_types,
                   ROUND(COALESCE(SUM(a.balance), 0), 2) AS total_balance
            FROM customers c
            LEFT JOIN accounts a ON c.customer_id = a.customer_id
            WHERE c.customer_id = ?
            GROUP BY c.customer_id, c.name, c.age, c.gender
            LIMIT 1
            """;

        try (Connection connection = DriverManager.getConnection(SQLITE_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, customerId);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return "Customer not found.";
                }
                Object age = rs.getObject("age");
                return String.format(Locale.US,
                    "Customer profile for %s: name=%s, age=%s, gender=%s, accountTypes=%s, totalBalance=%.2f",
                    rs.getString("customer_id"),
                    rs.getString("name"),
                    age == null ? "unknown" : age,
                    rs.getString("gender"),
                    rs.getString("account_types"),
                    rs.getDouble("total_balance"));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("SQLite customer summary query failed", ex);
        }
    }

    private String getMatchingProductsSqlite(String customerId, String preference) {
        String sql = """
            SELECT product_type, ROUND(SUM(balance), 2) AS balance,
                CASE
                    WHEN LOWER(product_type) LIKE '%current%' THEN 1
                    WHEN LOWER(product_type) LIKE '%savings%' THEN 2
                    WHEN LOWER(product_type) LIKE '%isa%' THEN 3
                    ELSE 4
                END AS liquidity_rank
            FROM accounts
            WHERE customer_id = ?
            GROUP BY product_type
            ORDER BY liquidity_rank, balance DESC
            """;

        List<String> ranked = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(SQLITE_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, customerId);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ranked.add(String.format(Locale.US, "%s(balance=%.2f, liquidityRank=%d)",
                        rs.getString("product_type"),
                        rs.getDouble("balance"),
                        rs.getInt("liquidity_rank")));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("SQLite product matching query failed", ex);
        }
        return String.format("Matching products for %s with preference=%s: %s", customerId, preference, ranked);
    }

    private List<String[]> loadTopTransactionsSqlite(String customerId, boolean credits) {
        String comparator = credits ? "> 0" : "< 0";
        String amountExpression = credits ? "ROUND(t.amount, 2)" : "ROUND(ABS(t.amount), 2)";
        String sql = """
            SELECT COALESCE(t.description, 'Unknown') AS description,
                   %s AS amount
            FROM accounts a
            JOIN transactions t ON a.account_id = t.account_id
            WHERE a.customer_id = ? AND t.amount %s
            ORDER BY ABS(t.amount) DESC
            LIMIT 5
            """.formatted(amountExpression, comparator);

        List<String[]> results = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(SQLITE_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, customerId);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    results.add(new String[]{rs.getString("description"), rs.getString("amount")});
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("SQLite top transactions query failed", ex);
        }
        return results;
    }

    private CustomerProfile loadProfileFromBigQuery(String customerId) throws Exception {
        String accounts = table("accounts");
        String transactions = table("transactions");
        String customers = table("customers");

        String sql = """
            WITH acct AS (
                SELECT customer_id,
                    ROUND(SUM(balance), 2) AS total_balance,
                    ROUND(SUM(CASE WHEN LOWER(product_type) LIKE '%savings%' OR LOWER(product_type) LIKE '%isa%' THEN balance ELSE 0 END), 2) AS savings_balance,
                    ROUND(SUM(CASE WHEN LOWER(product_type) LIKE '%current%' THEN balance ELSE 0 END), 2) AS current_balance
                FROM `%s` WHERE customer_id = @customerId GROUP BY customer_id
            ),
            txn AS (
                SELECT a.customer_id, COUNT(t.account_id) AS transaction_count,
                    ROUND(SUM(CASE WHEN t.amount > 0 THEN t.amount ELSE 0 END), 2) AS inflows,
                    ROUND(ABS(SUM(CASE WHEN t.amount < 0 THEN t.amount ELSE 0 END)), 2) AS outflows,
                    ROUND(AVG(CASE WHEN t.amount < 0 THEN ABS(t.amount) END), 2) AS monthly_spend,
                    ROUND(AVG(CASE WHEN t.amount > 0 THEN t.amount END), 2) AS monthly_income
                FROM `%s` a LEFT JOIN `%s` t ON a.account_id = t.account_id
                WHERE a.customer_id = @customerId GROUP BY a.customer_id
            )
            SELECT c.customer_id, c.name, c.age,
                COALESCE(acct.total_balance,0) AS total_balance,
                COALESCE(acct.savings_balance,0) AS savings_balance,
                COALESCE(acct.current_balance,0) AS current_balance,
                COALESCE(txn.transaction_count,0) AS transaction_count,
                COALESCE(txn.inflows,0) AS inflows,
                COALESCE(txn.outflows,0) AS outflows,
                COALESCE(txn.monthly_spend,0) AS monthly_spend,
                COALESCE(txn.monthly_income,0) AS monthly_income
            FROM `%s` c
            LEFT JOIN acct ON c.customer_id = acct.customer_id
            LEFT JOIN txn ON c.customer_id = txn.customer_id
            WHERE c.customer_id = @customerId LIMIT 1
            """.formatted(accounts, accounts, transactions, customers);

        FieldValueList row = firstRow(runBigQuery(sql, customerId));
        if (row == null) {
            return null;
        }

        Long age = longValue(row, "age");
        Long transactionCount = longValue(row, "transaction_count");

        return CustomerProfile.builder()
            .customerId(stringValue(row, "customer_id"))
            .name(stringValue(row, "name"))
            .age(age == null ? null : age.intValue())
            .totalBalance(doubleValue(row, "total_balance"))
            .savingsBalance(doubleValue(row, "savings_balance"))
            .currentBalance(doubleValue(row, "current_balance"))
            .transactionCount(transactionCount == null ? 0 : transactionCount.intValue())
            .inflows(doubleValue(row, "inflows"))
            .outflows(doubleValue(row, "outflows"))
            .monthlySpend(doubleValue(row, "monthly_spend"))
            .monthlyIncome(doubleValue(row, "monthly_income"))
            .topDebits(loadTopTransactionsBigQuery(customerId, false))
            .topCredits(loadTopTransactionsBigQuery(customerId, true))
            .build();
    }

    private String getCustomerProfileSummaryBigQuery(String customerId) throws Exception {
        String sql = """
            SELECT c.customer_id, c.name, c.age, c.gender,
                   STRING_AGG(DISTINCT a.product_type, ', ') AS account_types,
                   ROUND(COALESCE(SUM(a.balance), 0), 2) AS total_balance
            FROM `%s` c
            LEFT JOIN `%s` a ON c.customer_id = a.customer_id
            WHERE c.customer_id = @customerId
            GROUP BY c.customer_id, c.name, c.age, c.gender
            LIMIT 1
            """.formatted(table("customers"), table("accounts"));

        FieldValueList row = firstRow(runBigQuery(sql, customerId));
        if (row == null) {
            return "Customer not found.";
        }

        Long age = longValue(row, "age");
        return String.format(Locale.US,
            "Customer profile for %s: name=%s, age=%s, gender=%s, accountTypes=%s, totalBalance=%.2f",
            stringValue(row, "customer_id"),
            stringValue(row, "name"),
            age == null ? "unknown" : age,
            stringValue(row, "gender"),
            stringValue(row, "account_types"),
            doubleValue(row, "total_balance"));
    }

    private String getMatchingProductsBigQuery(String customerId, String preference) throws Exception {
        String sql = """
            SELECT product_type, ROUND(SUM(balance), 2) AS balance,
                CASE
                    WHEN LOWER(product_type) LIKE '%current%' THEN 1
                    WHEN LOWER(product_type) LIKE '%savings%' THEN 2
                    WHEN LOWER(product_type) LIKE '%isa%' THEN 3
                    ELSE 4
                END AS liquidity_rank
            FROM `%s`
            WHERE customer_id = @customerId
            GROUP BY product_type
            ORDER BY liquidity_rank, balance DESC
            """.formatted(table("accounts"));

        List<String> ranked = new ArrayList<>();
        for (FieldValueList row : runBigQuery(sql, customerId).iterateAll()) {
            Long liquidityRank = longValue(row, "liquidity_rank");
            ranked.add(String.format(Locale.US, "%s(balance=%.2f, liquidityRank=%d)",
                stringValue(row, "product_type"),
                doubleValue(row, "balance"),
                liquidityRank == null ? 0 : liquidityRank.intValue()));
        }
        return String.format("Matching products for %s with preference=%s: %s", customerId, preference, ranked);
    }

    private List<String[]> loadTopTransactionsBigQuery(String customerId, boolean credits) throws Exception {
        String comparator = credits ? "> 0" : "< 0";
        String amountExpression = credits ? "ROUND(t.amount, 2)" : "ROUND(ABS(t.amount), 2)";
        String sql = """
            SELECT COALESCE(t.description, 'Unknown') AS description,
                   %s AS amount
            FROM `%s` a
            JOIN `%s` t ON a.account_id = t.account_id
            WHERE a.customer_id = @customerId AND t.amount %s
            ORDER BY ABS(t.amount) DESC
            LIMIT 5
            """.formatted(amountExpression, table("accounts"), table("transactions"), comparator);

        List<String[]> results = new ArrayList<>();
        for (FieldValueList row : runBigQuery(sql, customerId).iterateAll()) {
            results.add(new String[]{stringValue(row, "description"), String.valueOf(doubleValue(row, "amount"))});
        }
        return results;
    }

    private TableResult runBigQuery(String sql, String customerId) throws Exception {
        log.debug("BigQuery query for customer={}: {}", customerId, sql);
        try {
            BigQuery bigQuery = BigQueryOptions.newBuilder()
                .setProjectId(googleCloudProject)
                .build()
                .getService();
            QueryJobConfiguration config = QueryJobConfiguration.newBuilder(sql)
                .addNamedParameter("customerId", QueryParameterValue.string(customerId))
                .build();
            JobId jobId = JobId.of(UUID.randomUUID().toString());
            Job job = bigQuery.create(JobInfo.newBuilder(config).setJobId(jobId).build());
            job = job.waitFor();
            if (job == null) {
                throw new BigQueryException(0, "BigQuery job no longer exists.");
            }
            if (job.getStatus().getError() != null) {
                throw new BigQueryException(0, job.getStatus().getError().toString());
            }
            log.debug("BigQuery query completed for customer={}", customerId);
            return job.getQueryResults();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        } catch (Exception ex) {
            log.warn("BigQuery error for customer={}: {} ({})", customerId, ex.getMessage(), ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private boolean shouldUseBigQuery() {
        return StringUtils.hasText(bqDataset) && StringUtils.hasText(googleCloudProject);
    }

    private String table(String tableName) {
        return googleCloudProject + "." + bqDataset + "." + tableName;
    }

    private Map<String, Object> product(String name, double annualRatePct, String liquidity, boolean taxWrapper) {
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("name", name);
        product.put("annualRatePct", annualRatePct);
        product.put("liquidity", liquidity);
        product.put("taxWrapper", taxWrapper);
        return product;
    }

    private FieldValueList firstRow(TableResult result) {
        for (FieldValueList row : result.iterateAll()) {
            return row;
        }
        return null;
    }

    private String stringValue(FieldValueList row, String fieldName) {
        try {
            com.google.cloud.bigquery.FieldValue fv = row.get(fieldName);
            if (fv == null || fv.isNull()) return null;
            return fv.getStringValue();
        } catch (Exception e) {
            log.debug("stringValue({}) failed: {}", fieldName, e.getMessage());
            return null;
        }
    }

    private Double doubleValue(FieldValueList row, String fieldName) {
        try {
            com.google.cloud.bigquery.FieldValue fv = row.get(fieldName);
            if (fv == null || fv.isNull()) return 0.0;
            // Always use getStringValue() — avoids type-conversion exceptions
            // for NUMERIC, BIGNUMERIC, FLOAT64, INT64 all serialise to plain string
            String s = fv.getStringValue();
            return (s == null || s.isBlank()) ? 0.0 : Double.parseDouble(s);
        } catch (Exception e) {
            log.debug("doubleValue({}) failed: {}", fieldName, e.getMessage());
            return 0.0;
        }
    }

    private Long longValue(FieldValueList row, String fieldName) {
        try {
            com.google.cloud.bigquery.FieldValue fv = row.get(fieldName);
            if (fv == null || fv.isNull()) return null;
            String s = fv.getStringValue();
            if (s == null || s.isBlank()) return null;
            return (long) Double.parseDouble(s); // handles "52" or "52.0"
        } catch (Exception e) {
            log.debug("longValue({}) failed: {}", fieldName, e.getMessage());
            return null;
        }
    }

    private String formatPairs(List<String[]> rows) {
        if (rows == null) {
            return "[]";
        }
        return rows.stream()
            .map(row -> row[0] + "=" + row[1])
            .toList()
            .toString();
    }
}
