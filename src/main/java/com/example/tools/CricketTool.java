package com.example.tools;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CricketTool implements Tool {
    @Override
    public String getName() {
        return "getScore";
    }

    @Override
    public String getDescription() {
        return "Get score of IPL match";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "team", Map.of(
                                "type", "string",
                                "description", "Team name"
                        )
                ),
                "required", List.of("team")
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        String team = (String) input.get("team");
        return Map.of("result", "Score of " + team + " is 230/4 in 20 Overs");
    }
}
