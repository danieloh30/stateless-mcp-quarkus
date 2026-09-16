package dev.helios.agent.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

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
        given().when().get("/")
                .then().statusCode(200)
                .body(containsString("Helios"));
    }

    @Test
    void defaultsToOneReplicaWithoutFleetDiscoveryConfigured() {
        given().when().get("/agent/runtime")
                .then().statusCode(200)
                .body("readyReplicas", is(1))
                .body("scaleToZero", is(false));
    }
}
