package dev.helios.agent.agents;

import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.service.V;

/**
 * The top-level agent: a supervisor that reads a natural-language logistics question,
 * decides which specialist sub-agent(s) to invoke (each backed by a slice of the stateless
 * MCP fleet), and returns a synthesized answer.
 *
 * <p>Quarkus registers this interface as an {@code @ApplicationScoped} CDI bean; inject it
 * wherever the answer is needed (see {@link dev.helios.agent.rest.AgentResource}).
 */
public interface HeliosSupervisor {

    @SupervisorAgent(
            responseStrategy = SupervisorResponseStrategy.SUMMARY,
            subAgents = { ShipmentAgent.class, InventoryAgent.class, ExceptionAgent.class })
    String ask(@V("request") String request);
}
