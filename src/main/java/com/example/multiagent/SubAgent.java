package com.example.multiagent;

import java.util.Map;

public interface SubAgent {
    String getName();
    String getDescription();
    Map<String, Object> getInputSchema();
    String run(String task) throws Exception;
}