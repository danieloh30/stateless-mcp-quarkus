package dev.helios.agent.dto;

/** A natural-language question posted to the supervisor via {@code POST /agent/ask}. */
public record AskRequest(String question) {
}
