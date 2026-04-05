package com.example.controller;

import com.example.agent.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final AgentService agentService;

    @GetMapping("/ask")
    public String ask(@RequestParam String q) throws Exception {
        return agentService.ask(q);
    }
}