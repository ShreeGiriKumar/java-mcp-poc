package com.example.agent;

import com.example.tools.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final List<Tool> tools;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.base.url}")
    private String baseUrl;

    public String ask(String userInput) throws Exception {

        Map<String, Object> request = new HashMap<>();

        request.put("model", "llama-3.3-70b-versatile");

        request.put("messages", List.of(
                Map.of("role", "user", "content", userInput)
        ));

        request.put("tools", buildToolDefinitions());
        request.put("tool_choice", "auto");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(request, headers);

        ResponseEntity<Map> response =
                restTemplate.postForEntity(baseUrl, entity, Map.class);

        Map body = response.getBody();
        Map message = (Map) ((List<Map>) body.get("choices")).get(0).get("message");

        // TOOL CALL
        if (message.containsKey("tool_calls")) {

            Map toolCall = ((List<Map>) message.get("tool_calls")).get(0);
            Map function = (Map) toolCall.get("function");

            String toolName = (String) function.get("name");

            Map<String, Object> args =
                    objectMapper.readValue(
                            (String) function.get("arguments"),
                            Map.class
                    );

            Tool tool = tools.stream()
                    .filter(t -> t.getName().equals(toolName))
                    .findFirst()
                    .orElseThrow();

            Map<String, Object> result = tool.execute(args);

            return sendToolResult(userInput, toolName, args, result);
        }

        return (String) message.get("content");
    }

    private String sendToolResult(String userInput,
                                 String toolName,
                                 Map<String, Object> args,
                                 Map<String, Object> result) {

        Map<String, Object> request = new HashMap<>();

        request.put("model", "llama-3.3-70b-versatile");

        request.put("messages", List.of(
                Map.of("role", "user", "content", userInput),
                Map.of("role", "assistant",
                        "tool_calls", List.of(
                                Map.of(
                                        "id", "call_1",
                                        "type", "function",
                                        "function", Map.of(
                                                "name", toolName,
                                                "arguments", args
                                        )
                                )
                        )),
                Map.of("role", "tool",
                        "tool_call_id", "call_1",
                        "content", result.toString())
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(request, headers);

        ResponseEntity<Map> response =
                restTemplate.postForEntity(baseUrl, entity, Map.class);

        Map body = response.getBody();
        return (String) ((Map) ((List<Map>) body.get("choices")).get(0)
                .get("message")).get("content");
    }

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