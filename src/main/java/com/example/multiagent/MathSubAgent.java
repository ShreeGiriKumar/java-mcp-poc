package com.example.multiagent;

import com.example.tools.CalculatorTool;
import com.example.tools.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MathSubAgent extends BaseSubAgent {

    private final CalculatorTool calculatorTool;

    @Override
    public String getName() {
        return "math_agent";
    }

    @Override
    public String getDescription() {
        return "Handles arithmetic and calculation tasks (addition, subtraction, multiplication, division).";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "task", Map.of("type", "string", "description", "The math problem to solve")
                ),
                "required", List.of("task")
        );
    }

    @Override
    protected List<Tool> getTools() {
        return List.of(calculatorTool);
    }

    @Override
    protected String getSystemPrompt() {
        return "You are a math specialist. Solve arithmetic problems using the calculate tool. Show the result clearly.";
    }
}