package com.example.tools;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CalculatorTool implements Tool {
    @Override
    public String getName() {
        return "calculate";
    }

    @Override
    public String getDescription() {
        return "Perform mathematical calculations like addition, multiplication, division";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "expression", Map.of(
                                "type", "string",
                                "description", "Mathematical expression like 25*4 or 100/5"
                        )
                ),
                "required", List.of("expression")
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        try {
            String expr = (String) input.get("expression");

            if (expr.contains("*")) {
                String[] parts = expr.split("\\*");
                double result = Double.parseDouble(parts[0].trim()) *
                        Double.parseDouble(parts[1].trim());
                return Map.of("result", result);
            }

            if (expr.contains("/")) {
                String[] parts = expr.split("/");
                double result = Double.parseDouble(parts[0].trim()) /
                        Double.parseDouble(parts[1].trim());
                return Map.of("result", result);
            }

            if (expr.contains("+")) {
                String[] parts = expr.split("\\+");
                double result = Double.parseDouble(parts[0].trim()) +
                        Double.parseDouble(parts[1].trim());
                return Map.of("result", result);
            }

            if (expr.contains("-")) {
                String[] parts = expr.split("\\-");
                double result = Double.parseDouble(parts[0].trim()) -
                        Double.parseDouble(parts[1].trim());
                return Map.of("result", result);
            }

            return Map.of("error", "Unsupported expression");

        } catch (Exception e) {
            return Map.of("error", "Invalid calculation");
        }
    }
}