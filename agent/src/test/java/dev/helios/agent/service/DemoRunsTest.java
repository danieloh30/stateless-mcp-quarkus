package dev.helios.agent.service;

import static org.junit.jupiter.api.Assertions.*;

import dev.helios.agent.dto.InvokeResult;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.*;

class DemoRunsTest {
    @Test
    void concurrentQuestionsNeverShareToolEvidence() throws Exception {
        var runs = new DemoRuns();
        runs.start("first");
        runs.start("second");
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first =
                    pool.submit(
                            () ->
                                    runs.record(
                                            "first",
                                            "Shipment",
                                            new InvokeResult(
                                                    "shipment",
                                                    Map.of("id", "A"),
                                                    false,
                                                    1,
                                                    Map.of())));
            var second =
                    pool.submit(
                            () ->
                                    runs.record(
                                            "second",
                                            "Inventory",
                                            new InvokeResult(
                                                    "inventory",
                                                    Map.of("id", "B"),
                                                    false,
                                                    1,
                                                    Map.of())));
            first.get();
            second.get();
        }
        var first = runs.finish("first");
        var second = runs.finish("second");
        assertEquals(1, first.size());
        assertEquals(1, second.size());
        assertTrue(first.toString().contains("Shipment"));
        assertFalse(first.toString().contains("Inventory"));
        assertTrue(second.toString().contains("Inventory"));
        assertFalse(second.toString().contains("Shipment"));
        runs.record("first", "late", new InvokeResult("late", Map.of(), false, 1, Map.of()));
        assertTrue(runs.finish("first").isEmpty());
    }
}
