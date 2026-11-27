package dev.helios.agent.dto;

import java.util.List;

public record AskResult(String answer, List<Object> trace) {}
