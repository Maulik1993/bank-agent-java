package com.bank.agent.controller;

import com.bank.agent.observability.ObservabilityStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/obs")
@RequiredArgsConstructor
public class ObsController {

    private final ObservabilityStore store;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(store.getToolStats());
    }

    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> tools() {
        return ResponseEntity.ok(store.getToolStats());
    }

    @GetMapping("/tools/sessions")
    public ResponseEntity<Object> sessions() {
        return ResponseEntity.ok(store.listSessionsWithTools());
    }

    @GetMapping("/tools/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> session(@PathVariable String sessionId) {
        return ResponseEntity.ok(store.getSessionToolCalls(sessionId));
    }

    @GetMapping("/coverage")
    public ResponseEntity<Map<String, Object>> coverage() {
        return ResponseEntity.ok(store.getToolCoverage());
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset() {
        store.reset();
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
