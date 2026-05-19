package com.example.controller;

import com.example.agent.AgentService;
import com.example.multiagent.OrchestratorAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final AgentService agentService;
    private final OrchestratorAgentService orchestratorAgentService;

    @GetMapping("/ask")
    public String ask(@RequestParam String q,
                      @RequestParam(defaultValue = "default") String session)
            throws Exception {

        return agentService.ask(q, session);
    }

    @GetMapping("/multi-agent")
    public String multiAgent(@RequestParam String q) throws Exception {
        return orchestratorAgentService.orchestrate(q);
    }
}