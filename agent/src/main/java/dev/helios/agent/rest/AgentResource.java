package dev.helios.agent.rest;

import dev.helios.agent.agents.HeliosSupervisor;
import dev.helios.agent.dto.*;
import dev.helios.agent.service.*;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.*;
import java.util.concurrent.*;

@jakarta.enterprise.context.ApplicationScoped
@Path("/agent")
@Produces(MediaType.APPLICATION_JSON)
public class AgentResource {
    private static final Logger LOG = Logger.getLogger(AgentResource.class);
    @Inject AgentService agent;
    @Inject HeliosSupervisor supervisor;
    @Inject DemoRuns runs;
    @Inject FleetReadiness readiness;

    @ConfigProperty(name = "quarkus.langchain4j.openai.api-key")
    String llmKey;

    @ConfigProperty(name = "helios.fleet.discovery-host")
    Optional<String> discovery;

    @ConfigProperty(name = "helios.fleet.scale-to-zero", defaultValue = "false")
    boolean scaleToZero;

    @ConfigProperty(name = "helios.demo.environment", defaultValue = "dev")
    String environment;

    @ConfigProperty(name = "helios.demo.ask-timeout-seconds", defaultValue = "90")
    long askTimeout;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore askSlots = new Semaphore(2);

    @GET
    @Path("catalog")
    public List<Map<String, Object>> catalog() {
        return agent.catalog();
    }

    @POST
    @Path("invoke/{tool}")
    @Consumes(MediaType.APPLICATION_JSON)
    public InvokeResult invoke(@PathParam("tool") String tool, Map<String, Object> args) {
        return agent.invoke(tool, args);
    }

    @GET
    @Path("instance")
    public RestResponse<Object> instance(@QueryParam("wake") boolean wake) {
        if (scaleToZero && !wake) return RestResponse.noContent();
        return RestResponse.ok(agent.serverInstance());
    }

    @GET
    @Path("runtime")
    public Map<String, Object> runtime() {
        var result = new LinkedHashMap<String, Object>();
        result.put("environment", scaleToZero ? "knative" : environment);
        result.put("burstEnabled", !"dev".equals(environment));
        result.put("scaleToZero", scaleToZero);
        var ready = readiness.read(discovery.orElse(""));
        result.put("readyReplicas", ready.count());
        result.put("readinessSource", ready.source());
        result.put(
                "protocolVersion",
                "2026-07-28"); // Expected version; wire evidence is shown separately.
        return result;
    }

    @POST
    @Path("ask")
    @Consumes(MediaType.APPLICATION_JSON)
    public AskResult ask(AskRequest req) {
        String question = req == null ? null : req.question();
        if (question == null || question.isBlank())
            throw new IllegalArgumentException("Ask a question first.");
        if (question.length() > 2000)
            throw new IllegalArgumentException("Questions are limited to 2000 characters.");
        if ("demo-not-configured".equals(llmKey) || llmKey.isBlank())
            throw new WebApplicationException(
                    "Configure OPENAI_API_KEY to ask the agent. Direct tool calls work without it.",
                    503);
        if (!askSlots.tryAcquire())
            throw new WebApplicationException(
                    "Two agent requests are already running. Try again shortly.", 429);
        String runId = UUID.randomUUID().toString();
        runs.start(runId);
        supervisor.agentMonitor().setMaxRetainedSessions(8);
        Future<String> task =
                executor.submit(
                        () -> {
                            try {
                                return supervisor.ask(runId, question);
                            } finally {
                                askSlots.release();
                            }
                        });
        try {
            String answer = task.get(askTimeout, TimeUnit.SECONDS);
            for (var execution : supervisor.agentMonitor().successfulExecutionsFor(runId))
                agent.recordTrace(execution.topLevelInvocations(), runId);
            return new AskResult(answer, runs.finish(runId));
        } catch (TimeoutException e) {
            task.cancel(true);
            throw new WebApplicationException(
                    "Agent deadline exceeded. Direct tool calls remain available.", 504);
        } catch (InterruptedException e) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw new WebApplicationException("Agent request interrupted.", 503);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Agent provider request failed", e.getCause());
        } finally {
            runs.finish(runId);
        }
    }

    @ServerExceptionMapper
    public RestResponse<Map<String, String>> mapError(RuntimeException e) {
        int status = 502;
        String code = "UPSTREAM_UNAVAILABLE";
        String message =
                "A dependency is unavailable. Check the agent logs or use direct tool calls.";
        if (e instanceof WebApplicationException web) {
            status = web.getResponse().getStatus();
            code = "REQUEST_REJECTED";
            message = e.getMessage();
        } else if (hasTimeout(e)) {
            status = 504;
            code = "DEADLINE_EXCEEDED";
            message = "The fleet request timed out. Try again when it is ready.";
        } else if (e instanceof IllegalArgumentException
                || e instanceof dev.langchain4j.exception.ToolArgumentsException
                || (e instanceof dev.langchain4j.exception.ToolExecutionException
                        && e.getCause() == null)) {
            status = 400;
            code = "INVALID_TOOL_REQUEST";
            message = e.getMessage();
        }
        LOG.warnf("Agent request failed (%s): %s", code, e.getClass().getSimpleName());
        LOG.debug("Agent request failure details", e);
        return RestResponse.status(
                jakarta.ws.rs.core.Response.Status.fromStatusCode(status),
                Map.of("error", message == null ? code : message, "code", code));
    }

    private boolean hasTimeout(Throwable e) {
        for (int i = 0; e != null && i < 12; i++, e = e.getCause())
            if (e instanceof TimeoutException || e instanceof java.net.http.HttpTimeoutException)
                return true;
        return false;
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }
}
