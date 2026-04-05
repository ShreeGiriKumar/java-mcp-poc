package com.example.tools;

import java.util.Map;

public interface Tool {
    String getName();
    String getDescription();
    Map<String, Object> getParameters();
    Map<String, Object> execute(Map<String, Object> input);
}