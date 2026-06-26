package com.bank.agent.observability;

import com.bank.agent.service.BankAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class ToolTracingAspect {

    private final ObservabilityStore observabilityStore;

    @Around("@annotation(com.bank.agent.observability.TracedTool)")
    public Object traceToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String sessionId = BankAgentService.getCurrentSessionId();
        String toolName = joinPoint.getSignature().getName();
        long start = System.currentTimeMillis();
        boolean success = false;
        String error = null;

        try {
            Object result = joinPoint.proceed();
            success = true;
            return result;
        } catch (Throwable throwable) {
            error = throwable.getMessage();
            throw throwable;
        } finally {
            double duration = System.currentTimeMillis() - start;
            observabilityStore.recordToolCall(ToolCallRecord.builder()
                .sessionId(sessionId)
                .toolName(toolName)
                .error(error)
                .durationMs(duration)
                .success(success)
                .timestamp(System.currentTimeMillis())
                .build());
            log.info("Tool call: name={} session={} duration={}ms success={}", toolName, sessionId, duration, success);
        }
    }
}
