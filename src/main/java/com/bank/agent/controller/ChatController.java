package com.bank.agent.controller;

import com.bank.agent.service.BankAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final BankAgentService bankAgentService;

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> request) {
        String sessionId = request.getOrDefault("sessionId", UUID.randomUUID().toString());
        String message = request.get("message");
        String response = bankAgentService.chat(sessionId, message);
        return ResponseEntity.ok(Map.of("sessionId", sessionId, "response", response));
    }

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, String>> createSession() {
        String sessionId = UUID.randomUUID().toString();
        return ResponseEntity.ok(Map.of("sessionId", sessionId));
    }
}
