package dev.helios.agent.rest;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import dev.helios.agent.agents.HeliosSupervisor;
import dev.helios.agent.dto.AskRequest;
import dev.helios.agent.dto.AskResult;
import dev.helios.agent.dto.InvokeResult;
import dev.helios.agent.service.AgentService;
import io.quarkus.runtime.LaunchMode;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * REST API the browser console calls. Every endpoint delegates to the
 * {@link AgentService} MCP client, which reaches the MCP server fleet through the
 * load balancer — so the agent, not the browser, is the MCP client.
 */
@Path("/agent")
@Produces(MediaType.APPLICATION_JSON)
public class AgentResource {

    private static final Logger LOG = Logger.getLogger(AgentResource.class);

    @Inject
    AgentService agent;

    @Inject
    HeliosSupervisor supervisor;

    @ConfigProperty(name = "helios.fleet.discovery-host", defaultValue = "")
    String fleetDiscoveryHost;

    @GET
    @Path("catalog")
    public List<Map<String, Object>> catalog() {
        return agent.catalog();
    }

    @POST
    @Path("invoke/{tool}")
    public InvokeResult invoke(@PathParam("tool") String tool, Map<String, Object> args) {
        return new InvokeResult(tool, agent.invoke(tool, args));
    }

    @GET
    @Path("instance")
    public Object instance() {
        return agent.serverInstance();
    }

    /** UI capabilities vary between single-server dev mode and packaged deployments. */
    @GET
    @Path("runtime")
    public Map<String, Object> runtime() {
        return Map.of(
                "burstEnabled", LaunchMode.current() != LaunchMode.DEVELOPMENT,
                "readyReplicas", readyReplicas());
    }

    private int readyReplicas() {
        if (fleetDiscoveryHost.isBlank()) {
            return 1;
        }
        try {
            return InetAddress.getAllByName(fleetDiscoveryHost).length;
        } catch (Exception e) {
            LOG.debugf("Could not resolve ready MCP replicas from %s: %s", fleetDiscoveryHost, e.getMessage());
            return 0;
        }
    }

    /**
     * Natural-language endpoint: the supervisor routes the question to the right specialist
     * sub-agent(s), which call the stateless MCP fleet for live data, and returns an answer.
     */
    @POST
    @Path("ask")
    @Consumes(MediaType.APPLICATION_JSON)
    public AskResult ask(AskRequest req) {
        String question = req == null ? null : req.question();
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Ask a question first.");
        }
        return new AskResult(supervisor.ask(question));
    }

    @ServerExceptionMapper
    public RestResponse<Map<String, String>> mapError(RuntimeException e) {
        LOG.error("Agent request failed", e);
        return RestResponse.status(RestResponse.Status.BAD_REQUEST, Map.of("error", rootMessage(e)));
    }

    /** Unwrap wrapper exceptions so the client sees the actual reason, not "Failed to invoke...". */
    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return msg != null && !msg.isBlank() ? msg : root.getClass().getSimpleName();
    }
}
