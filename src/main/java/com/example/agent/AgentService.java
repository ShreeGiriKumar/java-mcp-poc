package com.example.agent;

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

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.base.url}")
    private String baseUrl;

    // 🔥 MAIN ENTRY
    public String ask(String userInput, String sessionId) throws Exception {

        log.info("User input: {}", userInput);

        // 🧠 Build message history
        List<Map<String, Object>> messages = new ArrayList<>();

        Map.of(
                "role", "system",
                "content",
                "You are a strict AI assistant.\n" +
                        "You MUST use tools when applicable.\n" +
                        "Do NOT write function calls manually.\n" +
                        "ONLY use the provided tools via tool_calls JSON.\n" +
                        "Use 'getWeather' for weather queries.\n" +
                        "Use 'getScore' for cricket score queries.\n" +
                        "Use 'calculate' for math.\n" +
                        "If a tool is needed, ALWAYS call it using tool_calls."
        );

        messages.addAll(memoryService.getHistory(sessionId));

        messages.add(Map.of("role", "user", "content", userInput));

        // 🔧 Prepare request
        Map<String, Object> request = new HashMap<>();
        request.put("model", "llama-3.3-70b-versatile");
        request.put("messages", messages);
        request.put("tools", buildToolDefinitions());
        request.put("tool_choice", "auto");

        Map response = callLLM(request);

        Map message = extractMessage(response);

        String finalResponse;

        // 🔥 TOOL CALL FLOW
        if (message.containsKey("tool_calls")) {

            Map toolCall = ((List<Map>) message.get("tool_calls")).get(0);
            Map function = (Map) toolCall.get("function");

            String toolName = (String) function.get("name");

            Map<String, Object> args =
                    objectMapper.readValue(
                            (String) function.get("arguments"),
                            Map.class
                    );

            log.info("Tool selected: {} with args {}", toolName, args);

            Tool tool = tools.stream()
                    .filter(t -> t.getName().equals(toolName))
                    .findFirst()
                    .orElseThrow();

            Map<String, Object> result = tool.execute(args);

            log.info("Tool result: {}", result);

            finalResponse = sendToolResultToLLM(messages, toolName, args, result);

        } else {
            finalResponse = (String) message.get("content");
        }

        // 💾 Save memory
        memoryService.addMessage(sessionId,
                Map.of("role", "user", "content", userInput));

        memoryService.addMessage(sessionId,
                Map.of("role", "assistant", "content", finalResponse));

        return finalResponse;
    }

    // 🔁 SECOND CALL (tool result back to LLM)
    private String sendToolResultToLLM(List<Map<String, Object>> messages,
                                       String toolName,
                                       Map<String, Object> args,
                                       Map<String, Object> result) throws Exception {

        List<Map<String, Object>> newMessages = new ArrayList<>(messages);

        newMessages.add(Map.of(
                "role", "assistant",
                "tool_calls", List.of(
                        Map.of(
                                "id", "call_1",
                                "type", "function",
                                "function", Map.of(
                                        "name", toolName,
                                        "arguments", objectMapper.writeValueAsString(args)
                                )
                        )
                )
        ));

        newMessages.add(Map.of(
                "role", "tool",
                "tool_call_id", "call_1",
                "content", objectMapper.writeValueAsString(result)
        ));

        Map<String, Object> request = new HashMap<>();
        request.put("model", "llama-3.3-70b-versatile");
        request.put("messages", newMessages);

        Map response = callLLM(request);

        Map message = extractMessage(response);

        return (String) message.get("content");
    }

    // 🌐 COMMON LLM CALL
    private Map callLLM(Map<String, Object> request) {

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(request, headers);

        ResponseEntity<Map> response =
                restTemplate.postForEntity(baseUrl, entity, Map.class);

        return response.getBody();
    }

    // 🧠 EXTRACT MESSAGE
    private Map extractMessage(Map response) {
        return (Map) ((List<Map>) response.get("choices")).get(0).get("message");
    }

    // 🧩 TOOL DEFINITIONS
    private List<Map<String, Object>> buildToolDefinitions() {
        return tools.stream().map(t -> Map.of(
                "type", "function",
                "function", Map.of(
                        "name", t.getName(),
                        "description", t.getDescription(),
                        "parameters", t.getParameters()
                )
        )).toList();
    }
}