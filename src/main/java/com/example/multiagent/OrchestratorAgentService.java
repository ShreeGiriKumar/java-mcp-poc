package com.example.multiagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Orchestrator that routes user requests to specialized sub-agents.
 *
 * Flow:
 *   User → Orchestrator (Sonnet 4.6) → routes via tool_use → SubAgent(s) (Haiku)
 *                                                              ↓
 *                                                     actual tools (Weather, Math, Cricket)
 *                                                              ↓
 *                                               Sub-agent returns result string
 *                                                              ↓
 *                                   Orchestrator synthesizes final answer
 *
 * The orchestrator can call multiple sub-agents in a single turn when the
 * user asks a compound question (e.g. "weather in NYC and what is 25*4?").
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrchestratorAgentService {

    private final List<SubAgent> subAgents;

    @Value("${claude.api.key}")
    private String apiKey;

    @Value("${claude.base.url}")
    private String baseUrl;

    private static final String ORCHESTRATOR_MODEL = "claude-sonnet-4-6";
    private static final int MAX_STEPS = 6;

    private static final String SYSTEM_PROMPT =
            "You are an orchestrator agent. You have specialized sub-agents available as tools.\n" +
            "Analyze the user's request and delegate to the appropriate sub-agent(s).\n" +
            "For compound questions, call multiple sub-agents simultaneously.\n" +
            "Synthesize their results into a single, cohesive answer.";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String orchestrate(String userInput) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", userInput));

        for (int step = 0; step < MAX_STEPS; step++) {
            Map<String, Object> request = new HashMap<>();
            request.put("model", ORCHESTRATOR_MODEL);
            request.put("system", SYSTEM_PROMPT);
            request.put("messages", messages);
            request.put("tools", buildSubAgentTools());
            request.put("max_tokens", 2048);

            Map response = callClaude(request);
            String stopReason = (String) response.get("stop_reason");
            List<Map> content = (List<Map>) response.get("content");

            log.info("[Orchestrator] step={} stop_reason={}", step, stopReason);

            if ("tool_use".equals(stopReason)) {
                // Collect all tool_use blocks — orchestrator may call multiple sub-agents at once
                List<Map<String, Object>> toolResults = new ArrayList<>();

                for (Map block : content) {
                    if (!"tool_use".equals(block.get("type"))) continue;

                    String agentName = (String) block.get("name");
                    String task = (String) ((Map<String, Object>) block.get("input")).get("task");

                    log.info("[Orchestrator] → delegating to '{}': {}", agentName, task);

                    SubAgent subAgent = subAgents.stream()
                            .filter(a -> a.getName().equals(agentName))
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("Sub-agent not found: " + agentName));

                    String result = safeRun(subAgent, task);
                    log.info("[Orchestrator] ← '{}' returned: {}", agentName, result);

                    toolResults.add(Map.of(
                            "type", "tool_result",
                            "tool_use_id", block.get("id"),
                            "content", result
                    ));
                }

                messages.add(Map.of("role", "assistant", "content", content));
                messages.add(Map.of("role", "user", "content", toolResults));
                continue;
            }

            if ("end_turn".equals(stopReason)) {
                return content.stream()
                        .filter(b -> "text".equals(b.get("type")))
                        .map(b -> (String) b.get("text"))
                        .findFirst()
                        .orElse("(no response)");
            }

            log.warn("[Orchestrator] Unexpected stop_reason: {}", stopReason);
            break;
        }

        return "Orchestrator could not complete the request.";
    }

    private String safeRun(SubAgent agent, String task) {
        try {
            return agent.run(task);
        } catch (Exception e) {
            log.error("Sub-agent {} failed", agent.getName(), e);
            return "Error from " + agent.getName() + ": " + e.getMessage();
        }
    }

    private List<Map<String, Object>> buildSubAgentTools() {
        return subAgents.stream().map(a -> Map.<String, Object>of(
                "name", a.getName(),
                "description", a.getDescription(),
                "input_schema", a.getInputSchema()
        )).toList();
    }

    private Map callClaude(Map<String, Object> request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");
        return restTemplate.postForEntity(
                baseUrl, new HttpEntity<>(request, headers), Map.class
        ).getBody();
    }
}