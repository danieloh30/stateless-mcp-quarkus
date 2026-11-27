package dev.helios.agent.rest;

import static io.restassured.RestAssured.given;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

import org.junit.jupiter.api.Test;

import java.util.Map;

@QuarkusTest
@TestProfile(AgentStartupTest.LocalDevConfig.class)
class AgentStartupTest {

    public static class LocalDevConfig implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // No LLM calls or cluster DNS are needed for startup and metadata.
            return Map.of(
                    "quarkus.langchain4j.openai.api-key", "test-key",
                    "helios.fleet.discovery-host", "");
        }
    }

    @Test
    void servesTheSpaWithoutFleetDiscoveryConfigured() {
        given().when().get("/").then().statusCode(200).body(containsString("Helios"));
    }

    @Test
    void reportsUnknownReadinessWithoutInventingAReadyReplica() {
        given().when()
                .get("/agent/runtime")
                .then()
                .statusCode(200)
                .body("readyReplicas", nullValue())
                .body("environment", is("dev"))
                .body("burstEnabled", is(false))
                .body("scaleToZero", is(false));
    }

    @Test
    void rejectsInvalidQuestionsBeforeCallingTheProvider() {
        given().contentType("application/json")
                .body(Map.of("question", " "))
                .post("/agent/ask")
                .then()
                .statusCode(400)
                .body("code", is("INVALID_TOOL_REQUEST"));
        given().contentType("application/json")
                .body(Map.of("question", "x".repeat(2001)))
                .post("/agent/ask")
                .then()
                .statusCode(400);
    }

    @Test
    void servesExternalConsoleAssets() {
        given().get("/console.js").then().statusCode(200).body(containsString("agent/invoke/"));
        given().get("/console.css").then().statusCode(200).body(containsString("presentation"));
    }
}
