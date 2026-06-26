package com.bank.agent.service;

import com.bank.agent.config.AgentConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankAgentService {

    private static final ThreadLocal<String> CURRENT_SESSION_ID = new ThreadLocal<>();

    private final AgentConfig.BankingAssistant assistant;

    public static String getCurrentSessionId() {
        return CURRENT_SESSION_ID.get();
    }

    public String chat(String sessionId, String message) {
        if (message == null || message.isBlank()) {
            return "Please provide a message.";
        }
        log.info("Chat request: session={} message={}", sessionId, message);
        CURRENT_SESSION_ID.set(sessionId);
        try {
            String response = assistant.chat(sessionId, message);
            log.info("Chat response: session={} responseLength={} response={}",
                sessionId,
                response == null ? 0 : response.length(),
                response == null ? "NULL" : response.substring(0, Math.min(200, response.length())));
            return response == null ? "No response from agent." : response;
        } finally {
            CURRENT_SESSION_ID.remove();
        }
    }
}
