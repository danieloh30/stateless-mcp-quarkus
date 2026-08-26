package dev.helios;

import java.time.Instant;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Identity of this running server instance.
 *
 * <p>Used purely to make statelessness visible in the demo: each replica gets a
 * random instance ID at startup, so the console can show that consecutive requests
 * may be served by different instances with identical results — no session
 * affinity required.
 */
@ApplicationScoped
public class InstanceInfo {

    private final String instanceId = "helios-" + UUID.randomUUID().toString().substring(0, 8);
    private final long startedAtEpochMs = Instant.now().toEpochMilli();

    public String instanceId() {
        return instanceId;
    }

    public long startedAtEpochMs() {
        return startedAtEpochMs;
    }

    public long uptimeMs() {
        return Instant.now().toEpochMilli() - startedAtEpochMs;
    }
}
