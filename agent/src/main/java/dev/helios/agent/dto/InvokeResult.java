package dev.helios.agent.dto;

import java.util.Map;

public record InvokeResult(
        String tool,
        Object result,
        boolean isError,
        long durationMs,
        Map<String, Object> evidence) {}
