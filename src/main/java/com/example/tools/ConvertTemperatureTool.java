package com.example.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ConvertTemperatureTool implements Tool {

    @Override
    public String getName() {
        return "convertTemperature";
    }

    @Override
    public String getDescription() {
        return "Convert temperature between Celsius and Fahrenheit";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "value", Map.of(
                                "type", "number",
                                "description", "Temperature value"
                        ),
                        "toUnit", Map.of(
                                "type", "string",
                                "description", "Target unit: C or F"
                        )
                ),
                "required", List.of("value", "toUnit")
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input) {

        try {
            double value = Double.parseDouble(input.get("value").toString());
            String toUnit = input.get("toUnit").toString().toUpperCase();

            log.info("Converting temperature: {} → {}", value, toUnit);

            double result;

            if ("F".equals(toUnit)) {
                // Celsius → Fahrenheit
                result = (value * 9 / 5) + 32;
                return Map.of(
                        "result", result,
                        "unit", "F"
                );
            } else if ("C".equals(toUnit)) {
                // Fahrenheit → Celsius
                result = (value - 32) * 5 / 9;
                return Map.of(
                        "result", result,
                        "unit", "C"
                );
            } else {
                return Map.of("error", "Invalid unit. Use 'C' or 'F'");
            }

        } catch (Exception e) {
            log.error("Temperature conversion failed", e);
            return Map.of("error", "Invalid temperature input");
        }
    }
}