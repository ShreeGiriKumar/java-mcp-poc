package com.example.multiagent;

import com.example.tools.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
public abstract class BaseSubAgent implements SubAgent {

    @Value("${claude.api.key}")
    protected String apiKey;

    @Value("${claude.base.url}")
    protected String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Sub-agents use Haiku — fast and cheap for focused, single-domain tasks
    private static final String SUB_AGENT_MODEL = "claude-haiku-4-5-20251001";
    private static final int MAX_STEPS = 5;

    protected abstract List<Tool> getTools();
    protected abstract String getSystemPrompt();

    @Override
    public String run(String task) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", task));

        for (int step = 0; step < MAX_STEPS; step++) {
            Map<String, Object> request = new HashMap<>();
            request.put("model", SUB_AGENT_MODEL);
            request.put("system", getSystemPrompt());
            request.put("messages", messages);
            request.put("max_tokens", 1024);

            List<Tool> tools = getTools();
            if (!tools.isEmpty()) {
                request.put("tools", buildToolDefs(tools));
            }

            Map response = callClaude(request);
            String stopReason = (String) response.get("stop_reason");

            log.info("[SubAgent:{}] step={} stop_reason={}", getName(), step, stopReason);

            if ("tool_use".equals(stopReason)) {
                List<Map> content = (List<Map>) response.get("content");
                Map toolBlock = content.stream()
                        .filter(b -> "tool_use".equals(b.get("type")))
                        .findFirst().orElse(null);

                if (toolBlock == null) break;

                String toolName = (String) toolBlock.get("name");
                Map<String, Object> input = (Map<String, Object>) toolBlock.get("input");

                Tool tool = tools.stream()
                        .filter(t -> t.getName().equals(toolName))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Tool not found: " + toolName));

                Map<String, Object> result = tool.execute(input);
                log.info("[SubAgent:{}] tool={} result={}", getName(), toolName, result);

                messages.add(Map.of("role", "assistant", "content", content));
                messages.add(Map.of("role", "user", "content", List.of(Map.of(
                        "type", "tool_result",
                        "tool_use_id", toolBlock.get("id"),
                        "content", objectMapper.writeValueAsString(result)
                ))));
                continue;
            }

            if ("end_turn".equals(stopReason)) {
                List<Map> content = (List<Map>) response.get("content");
                return content.stream()
                        .filter(b -> "text".equals(b.get("type")))
                        .map(b -> (String) b.get("text"))
                        .findFirst()
                        .orElse("(no response)");
            }

            log.warn("[SubAgent:{}] Unexpected stop_reason: {}", getName(), stopReason);
            break;
        }

        return "Sub-agent could not complete the task.";
    }

    private List<Map<String, Object>> buildToolDefs(List<Tool> tools) {
        return tools.stream().map(t -> Map.<String, Object>of(
                "name", t.getName(),
                "description", t.getDescription(),
                "input_schema", t.getParameters()
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