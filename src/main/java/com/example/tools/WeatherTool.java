package com.example.tools;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class WeatherTool implements Tool {

    @Override
    public String getName() {
        return "getWeather";
    }

    @Override
    public String getDescription() {
        return "Get weather for a city";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "city", Map.of(
                                "type", "string",
                                "description", "City name"
                        )
                ),
                "required", List.of("city")
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        String city = (String) input.get("city");
        return Map.of("result", "Weather in " + city + " is Sunny 25°C");
    }
}