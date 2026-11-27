package dev.helios.agent.service;

import dev.helios.agent.dto.InvokeResult;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Only in-flight, request-scoped evidence; never a global last-result buffer. */
@ApplicationScoped
public class DemoRuns {
    private final Map<String, List<Object>> active = new ConcurrentHashMap<>();

    public void start(String id) {
        active.put(id, new CopyOnWriteArrayList<>());
    }

    public void record(String id, String specialist, InvokeResult call) {
        var events = active.get(id);
        if (events != null) events.add(Map.of("specialist", specialist, "call", call));
    }

    public List<Object> finish(String id) {
        var events = active.remove(id);
        return events == null ? List.of() : List.copyOf(events);
    }
}
