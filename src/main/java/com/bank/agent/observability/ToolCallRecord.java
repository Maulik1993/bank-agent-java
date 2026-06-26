package com.bank.agent.observability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ToolCallRecord {
    private String sessionId;
    private String toolName;
    private String error;
    private double durationMs;
    private boolean success;
    @Default
    private long timestamp = System.currentTimeMillis();
}
