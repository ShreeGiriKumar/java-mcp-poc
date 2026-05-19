package com.example.multiagent;

import com.example.tools.ConvertTemperatureTool;
import com.example.tools.Tool;
import com.example.tools.WeatherTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WeatherSubAgent extends BaseSubAgent {

    private final WeatherTool weatherTool;
    private final ConvertTemperatureTool convertTemperatureTool;

    @Override
    public String getName() {
        return "weather_agent";
    }

    @Override
    public String getDescription() {
        return "Handles weather queries. Gets current temperature and wind speed for a city. Can also convert temperatures between Celsius and Fahrenheit.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "task", Map.of("type", "string", "description", "The weather question to answer")
                ),
                "required", List.of("task")
        );
    }

    @Override
    protected List<Tool> getTools() {
        return List.of(weatherTool, convertTemperatureTool);
    }

    @Override
    protected String getSystemPrompt() {
        return "You are a weather specialist. Answer weather questions using the provided tools. Be concise and include units.";
    }
}