package io.quarkiverse.resiliencytesting.it;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.is;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.resiliencytesting.ActiveFault;
import io.quarkiverse.resiliencytesting.Faults;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;

/**
 * A host name that stops resolving, for a dependency no proxy stands in front of: the REST Client looks the name up
 * through Netty's resolver, fails on it, and connects once the name resolves again.
 */
@QuarkusTest
@TestProfile(UnresolvableHostTest.PricingByName.class)
@QuarkusTestResource(InventoryServer.class)
public class UnresolvableHostTest {

    /**
     * The pricing service by name rather than by address, so that reaching it takes a lookup.
     */
    public static class PricingByName implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.rest-client.\"" + PricingClient.class.getName() + "\".url",
                    "http://localhost:" + PricingClient.PORT);
        }
    }

    @Inject
    Faults faults;

    @BeforeEach
    public void callTheApplicationByAddress() {
        // the fault is on the name wherever it is looked up, and by default the test calls the application by it
        RestAssured.baseURI = "http://127.0.0.1";
    }

    @Test
    public void aRestClientFailsOnTheLookupAndRecovers() {
        try (ActiveFault f = faults.host("localhost").unresolvable()) {
            when()
                    .get("/orders/price")
                    .then()
                    .statusCode(500);
        }

        when()
                .get("/orders/price")
                .then()
                .statusCode(200)
                .body(is("9.99"));
    }
}
