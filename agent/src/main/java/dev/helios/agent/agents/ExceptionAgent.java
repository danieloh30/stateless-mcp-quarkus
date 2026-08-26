package dev.helios.agent.agents;

import dev.helios.agent.tools.ExceptionToolbox;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import io.quarkiverse.langchain4j.ToolBox;

/**
 * Sub-agent for open shipment exceptions by region. See {@link ShipmentAgent} for the pattern.
 */
public interface ExceptionAgent {

    @SystemMessage("""
            You are a Helios Logistics operations specialist. Answer questions about open
            shipment exceptions needing attention in a region (EU, APAC, or NA). Always call the
            exceptions tool for live data. Summarize what needs action; never invent incidents.
            """)
    @UserMessage("{{request}}")
    @Agent(description = "Handles open shipment exceptions / incidents needing operator attention in a region",
            outputKey = "exceptionAnswer")
    @ToolBox(ExceptionToolbox.class)
    String handle(@V("request") String request);
}
