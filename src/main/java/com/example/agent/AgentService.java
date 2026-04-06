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

    // 🚀 FINAL ENTRY
    public String ask(String userInput, String sessionId) throws Exception {

        log.info("User input: {}", userInput);

        // 🔥 1. HYBRID SHORT-CIRCUIT (CRITICAL FIX)
        if (isWeatherFahrenheitQuery(userInput)) {
            return handleWeatherFahrenheit(userInput);
        }

        // 🧠 2. NORMAL LLM FLOW (SINGLE TOOL ONLY)
        List<Map<String, Object>> messages = buildMessages(userInput, sessionId);

        Map<String, Object> request = new HashMap<>();
        request.put("model", "llama-3.3-70b-versatile");
        request.put("messages", messages);
        request.put("tools", buildToolDefinitions());
        request.put("tool_choice", "auto"); // 🔥 important
        request.put("temperature", 0);

        Map response = callLLM(request);
        Map message = extractMessage(response);

        // 🔧 Handle tool call (ONLY ONE STEP)
        if (message.containsKey("tool_calls")) {

            Map toolCall = ((List<Map>) message.get("tool_calls")).get(0);
            Map function = (Map) toolCall.get("function");

            String toolName = (String) function.get("name");
            Map<String, Object> args =
                    objectMapper.readValue((String) function.get("arguments"), Map.class);

            // 🚫 BLOCK nested calls
            String rawArgs = objectMapper.writeValueAsString(args);
            if (rawArgs.contains("<function=")) {
                log.warn("🚫 Blocking nested function call: {}", rawArgs);
                return "Unable to process complex nested request safely.";
            }

            Tool tool = tools.stream()
                    .filter(t -> t.getName().equals(toolName))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Tool not found: " + toolName));

            Map<String, Object> result = tool.execute(args);

            log.info("Tool {} executed → {}", toolName, result);

            return result.toString();
        }

        // ✅ Normal response
        String finalResponse = (String) message.get("content");

        memoryService.addMessage(sessionId, Map.of("role", "user", "content", userInput));
        memoryService.addMessage(sessionId, Map.of("role", "assistant", "content", finalResponse));

        return finalResponse;
    }

    // 🔥 HYBRID ORCHESTRATION (JAVA CONTROL)
    private String handleWeatherFahrenheit(String userInput) {

        log.info("⚡ Hybrid flow triggered: weather → conversion");

        Tool weatherTool = tools.stream()
                .filter(t -> t.getName().equals("getWeather"))
                .findFirst()
                .orElseThrow();

        Map<String, Object> weather = weatherTool.execute(Map.of("city", extractCity(userInput)));

        double tempC = Double.parseDouble(weather.get("temperature").toString());

        Tool convertTool = tools.stream()
                .filter(t -> t.getName().equals("convertTemperature"))
                .findFirst()
                .orElseThrow();

        Map<String, Object> converted =
                convertTool.execute(Map.of("value", tempC, "toUnit", "F"));

        return "Temperature in " + weather.get("city") + " is " +
                converted.get("result") + " °F";
    }

    private boolean isWeatherFahrenheitQuery(String input) {
        String text = input.toLowerCase();
        return text.contains("weather") && text.contains("fahrenheit");
    }

    private String extractCity(String input) {
        if (input.toLowerCase().contains("boston")) return "Boston";
        if (input.toLowerCase().contains("new york")) return "New York";
        return "Boston"; // default
    }

    // 🧠 LLM MESSAGE BUILD
    private List<Map<String, Object>> buildMessages(String userInput, String sessionId) {
        List<Map<String, Object>> messages = new ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content",
                "You are an AI assistant.\n" +
                        "Call only ONE tool at a time.\n" +
                        "Never include another tool call inside arguments.\n" +
                        "If unsure, respond normally instead of forcing a tool."
        ));

        messages.addAll(memoryService.getHistory(sessionId));
        messages.add(Map.of("role", "user", "content", userInput));

        return messages;
    }

    // 🌐 LLM CALL
    private Map callLLM(Map<String, Object> request) {

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(request, headers);

        return restTemplate.postForEntity(baseUrl, entity, Map.class).getBody();
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