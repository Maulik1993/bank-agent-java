package com.bank.agent.observability;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Component
public class ObservabilityStore {

    private final CopyOnWriteArrayList<ToolCallRecord> records = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> registeredTools = new CopyOnWriteArrayList<>();

    public void recordToolCall(ToolCallRecord record) {
        records.add(record);
    }

    public void registerTools(List<String> toolNames) {
        registeredTools.clear();
        toolNames.stream().distinct().forEach(registeredTools::add);
    }

    public Map<String, Object> getToolStats() {
        Map<String, List<ToolCallRecord>> grouped = records.stream()
            .collect(Collectors.groupingBy(ToolCallRecord::getToolName));

        List<Map<String, Object>> tools = grouped.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> {
                List<ToolCallRecord> toolRecords = entry.getValue();
                long total = toolRecords.size();
                long success = toolRecords.stream().filter(ToolCallRecord::isSuccess).count();
                double avgDuration = toolRecords.stream().mapToDouble(ToolCallRecord::getDurationMs).average().orElse(0.0);

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("toolName", entry.getKey());
                item.put("callCount", total);
                item.put("successCount", success);
                item.put("successRate", total == 0 ? 0.0 : (success * 100.0) / total);
                item.put("avgDurationMs", avgDuration);
                return item;
            })
            .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCalls", records.size());
        result.put("toolCount", grouped.size());
        result.put("tools", tools);
        return result;
    }

    public Map<String, Object> getToolCoverage() {
        List<String> calledTools = records.stream()
            .map(ToolCallRecord::getToolName)
            .distinct()
            .sorted()
            .toList();

        List<String> missing = registeredTools.stream()
            .filter(tool -> !calledTools.contains(tool))
            .sorted()
            .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registeredTools", new ArrayList<>(registeredTools));
        result.put("calledTools", calledTools);
        result.put("registeredCount", registeredTools.size());
        result.put("calledCount", calledTools.size());
        result.put("coveragePct", registeredTools.isEmpty() ? 0.0 : (calledTools.size() * 100.0) / registeredTools.size());
        result.put("missingTools", missing);
        return result;
    }

    public Map<String, Object> getSessionToolCalls(String sessionId) {
        List<ToolCallRecord> sessionRecords = records.stream()
            .filter(record -> sessionId.equals(record.getSessionId()))
            .sorted(Comparator.comparingLong(ToolCallRecord::getTimestamp))
            .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("callCount", sessionRecords.size());
        result.put("calls", sessionRecords);
        return result;
    }

    public List<Map<String, Object>> listSessionsWithTools() {
        Map<String, List<ToolCallRecord>> grouped = records.stream()
            .collect(Collectors.groupingBy(ToolCallRecord::getSessionId));

        return grouped.entrySet().stream()
            .map(entry -> {
                List<ToolCallRecord> sessionRecords = entry.getValue();
                long latestTimestamp = sessionRecords.stream().mapToLong(ToolCallRecord::getTimestamp).max().orElse(0L);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("sessionId", entry.getKey());
                item.put("callCount", sessionRecords.size());
                item.put("latestTimestamp", latestTimestamp);
                item.put("tools", sessionRecords.stream().map(ToolCallRecord::getToolName).distinct().toList());
                return item;
            })
            .sorted((a, b) -> Long.compare((Long) b.get("latestTimestamp"), (Long) a.get("latestTimestamp")))
            .toList();
    }

    public void reset() {
        records.clear();
    }
}
