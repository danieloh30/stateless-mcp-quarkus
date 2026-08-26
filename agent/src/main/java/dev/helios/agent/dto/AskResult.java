package dev.helios.agent.dto;

/** The supervisor's synthesized answer returned from {@code POST /agent/ask}. */
public record AskResult(String answer) {
}
