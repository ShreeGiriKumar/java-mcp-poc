package com.example.agent;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ChatMemoryService {

    private final Map<String, List<Map<String, Object>>> memory = new HashMap<>();

    public List<Map<String, Object>> getHistory(String sessionId) {
        return memory.computeIfAbsent(sessionId, k -> new ArrayList<>());
    }

    public void addMessage(String sessionId, Map<String, Object> message) {
        memory.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
    }
}