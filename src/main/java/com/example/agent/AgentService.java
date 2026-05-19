package com.example.agent;

import com.anthropic.models.messages.Model;
import com.example.tools.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentService {

    private final List<Tool> tools;
    private final ChatMemoryService memoryService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${claude.api.key}")
    private String apiKey;

    @Value("${claude.base.url}")
    private String baseUrl;

    private static final int MAX_STEPS = 8;

    public String ask(String userInput, String sessionId) throws Exception {

        List<Map<String, Object>> messages = buildMessages(userInput, sessionId);

        String systemPrompt =
                "You are a production AI agent.\n" +
                        "Use tools when required.\n" +
                        "Wait for tool results before continuing.\n" +
                        "Do not hallucinate tool outputs.";

        for (int step = 0; step < MAX_STEPS; step++) {

            Map<String, Object> request = new HashMap<>();
            request.put("model", Model.CLAUDE_OPUS_4_6);
            request.put("system", systemPrompt);
            request.put("messages", messages);
            request.put("tools", buildToolDefinitions());
            request.put("max_tokens", 1024);

            Map response = callClaude(request);
            String stopReason = getStopReason(response);

            log.info("STEP {} stop_reason={} response={}", step, stopReason, response);

            // ===============================
            // 1. TOOL REQUEST PATH
            // ===============================
            if ("tool_use".equals(stopReason)) {

                // Find the tool_use block specifically (text block may also be present)
                Map toolUseBlock = findToolUseBlock(response);

                if (toolUseBlock == null) {
                    log.error("stop_reason was tool_use but no tool_use block found");
                    break;
                }

                String toolName = (String) toolUseBlock.get("name");
                Map<String, Object> input = (Map<String, Object>) toolUseBlock.get("input");

                log.info("Tool requested: {} args={}", toolName, input);

                Tool tool = findTool(toolName);
                Map<String, Object> toolResult = safeExecute(tool, input);
                String toolResultString = objectMapper.writeValueAsString(toolResult);

                log.info("Tool result: {}", toolResultString);

                // Append the FULL content array as the assistant message
                // (Claude requires the entire content array, not just the tool_use block)
                messages.add(Map.of(
                        "role", "assistant",
                        "content", response.get("content")
                ));

                // Append tool result as user message
                messages.add(Map.of(
                        "role", "user",
                        "content", List.of(Map.of(
                                "type", "tool_result",
                                "tool_use_id", toolUseBlock.get("id"),
                                "content", toolResultString
                        ))
                ));

                continue;
            }

            // ===============================
            // 2. FINAL ANSWER PATH
            // ===============================
            if ("end_turn".equals(stopReason)) {

                String finalAnswer = extractText(response);

                memoryService.addMessage(sessionId,
                        Map.of("role", "user", "content", userInput));

                memoryService.addMessage(sessionId,
                        Map.of("role", "assistant", "content", finalAnswer));

                return finalAnswer;
            }

            // ===============================
            // 3. UNEXPECTED STOP REASON
            // ===============================
            log.warn("Unexpected stop_reason: {}", stopReason);
            break;
        }

        // ===============================
        // 4. SAFETY FALLBACK
        // ===============================
        log.error("Agent stopped after max steps or unexpected state");

        return "I couldn't complete this request after multiple reasoning steps. " +
                "Please rephrase or simplify the query.";
    }

    // ===============================
    // STOP REASON EXTRACTION
    // ===============================
    private String getStopReason(Map response) {
        return (String) response.get("stop_reason");
    }

    // ===============================
    // FIND TOOL_USE BLOCK IN RESPONSE
    // ===============================
    private Map findToolUseBlock(Map response) {
        List<Map> content = (List<Map>) response.get("content");
        return content.stream()
                .filter(block -> "tool_use".equals(block.get("type")))
                .findFirst()
                .orElse(null);
    }

    // ===============================
    // EXTRACT TEXT FROM RESPONSE
    // ===============================
    private String extractText(Map response) {
        List<Map> content = (List<Map>) response.get("content");
        return content.stream()
                .filter(block -> "text".equals(block.get("type")))
                .map(block -> block.get("text").toString())
                .findFirst()
                .orElse("(no text response)");
    }

    // ===============================
    // TOOL EXECUTION SAFETY LAYER
    // ===============================
    private Map<String, Object> safeExecute(Tool tool, Map<String, Object> input) {
        try {
            log.info("Executing tool {} with {}", tool.getName(), input);

            Map<String, Object> result = tool.execute(input);

            if (result == null) {
                return Map.of("error", "Tool returned null result");
            }

            return result;

        } catch (Exception e) {
            log.error("Tool execution failed: {}", tool.getName(), e);

            return Map.of(
                    "error", "Tool execution failed",
                    "tool", tool.getName()
            );
        }
    }

    // ===============================
    // FIND TOOL BY NAME
    // ===============================
    private Tool findTool(String name) {
        return tools.stream()
                .filter(t -> t.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Tool not found: " + name));
    }

    // ===============================
    // CLAUDE API CALL
    // ===============================
    private Map callClaude(Map<String, Object> request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        return restTemplate.postForEntity(baseUrl, entity, Map.class).getBody();
    }

    // ===============================
    // BUILD INITIAL MESSAGES
    // ===============================
    private List<Map<String, Object>> buildMessages(String userInput, String sessionId) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.addAll(memoryService.getHistory(sessionId));
        messages.add(Map.of("role", "user", "content", userInput));
        return messages;
    }

    // ===============================
    // TOOL DEFINITIONS (CLAUDE FORMAT)
    // ===============================
    private List<Map<String, Object>> buildToolDefinitions() {
        return tools.stream().map(t -> Map.of(
                "name", t.getName(),
                "description", t.getDescription(),
                "input_schema", t.getParameters()
        )).toList();
    }
}