package com.example.multiagent;

import com.example.tools.CricketTool;
import com.example.tools.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CricketSubAgent extends BaseSubAgent {

    private final CricketTool cricketTool;

    @Override
    public String getName() {
        return "cricket_agent";
    }

    @Override
    public String getDescription() {
        return "Handles IPL cricket match score queries. Looks up the current score for a given team.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "task", Map.of("type", "string", "description", "The cricket query, e.g. what is CSK's score")
                ),
                "required", List.of("task")
        );
    }

    @Override
    protected List<Tool> getTools() {
        return List.of(cricketTool);
    }

    @Override
    protected String getSystemPrompt() {
        return "You are a cricket specialist. Look up IPL match scores using the getScore tool and report them clearly.";
    }
}