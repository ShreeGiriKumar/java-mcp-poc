package com.example.tools;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WeatherTool implements Tool {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getName() {
        return "getWeather";
    }

    @Override
    public String getDescription() {
        return "Get current weather for a city";
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

        // Simple mapping (for PoC)
        double lat = 42.3601; // default Boston
        double lon = -71.0589;

        if (city.toLowerCase().contains("new york")) {
            lat = 40.7128;
            lon = -74.0060;
        }

        String url = "https://api.open-meteo.com/v1/forecast?latitude="
                + lat + "&longitude=" + lon + "&current_weather=true";

        Map response = restTemplate.getForObject(url, Map.class);

        Map current = (Map) response.get("current_weather");

        return Map.of(
                "city", city,
                "temperature", current.get("temperature"),
                "windspeed", current.get("windspeed")
        );
    }
}