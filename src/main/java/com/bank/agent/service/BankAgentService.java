package com.bank.agent.service;

import com.bank.agent.config.AgentConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BankAgentService {

    private static final ThreadLocal<String> CURRENT_SESSION_ID = new ThreadLocal<>();

    private final AgentConfig.BankingAssistant assistant;

    public static String getCurrentSessionId() {
        return CURRENT_SESSION_ID.get();
    }

    public String chat(String sessionId, String message) {
        CURRENT_SESSION_ID.set(sessionId);
        try {
            return assistant.chat(sessionId, message);
        } finally {
            CURRENT_SESSION_ID.remove();
        }
    }
}
