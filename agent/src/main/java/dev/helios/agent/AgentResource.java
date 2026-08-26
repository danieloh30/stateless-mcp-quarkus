package dev.helios.agent;

import java.util.List;
import java.util.Map;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import jakarta.inject.Inject;
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

    @Inject
    AgentService agent;

    public record InvokeResult(String tool, Object result) {
    }

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

    @ServerExceptionMapper
    public RestResponse<Map<String, String>> mapError(RuntimeException e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return RestResponse.status(RestResponse.Status.BAD_REQUEST, Map.of("error", msg));
    }
}
