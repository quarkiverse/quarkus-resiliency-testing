package io.quarkiverse.resiliencytesting.it;

import static io.restassured.RestAssured.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import jakarta.inject.Inject;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.resiliencytesting.ActiveFault;
import io.quarkiverse.resiliencytesting.Faults;
import io.quarkiverse.resiliencytesting.HttpRequestRecord;
import io.quarkiverse.resiliencytesting.Target;
import io.quarkiverse.resiliencytesting.junit.NetworkFaultsProfile;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * REST Clients built in code, here before each test, with a builder and a base URI, rather than registered and
 * configured: {@link QuoteClient} with the builder of Quarkus and {@link RateClient} with the one of MicroProfile.
 * Neither interface has {@code @RegisterRestClient} or a {@code quarkus.rest-client} property. Both are proxied
 * all the same, and addressed by their interface.
 */
@QuarkusTest
@TestProfile(NetworkFaultsProfile.class)
@QuarkusTestResource(InventoryServer.class)
public class ProgrammaticRestClientTest {

    // the pricing service of InventoryServer, which answers every request with this price
    private static final URI PRICING = URI.create("http://127.0.0.1:" + PricingClient.PORT);
    private static final String PRICE = "9.99";

    @Inject
    Faults faults;

    private QuoteClient quotes;
    private RateClient rates;

    @BeforeEach
    void buildTheClients() throws MalformedURLException {
        quotes = QuarkusRestClientBuilder.newBuilder()
                .baseUri(PRICING)
                .build(QuoteClient.class);
        rates = RestClientBuilder.newBuilder()
                .baseUrl(PRICING.toURL())
                .build(RateClient.class);
    }

    @Test
    public void theClientResolvesByItsInterface() {
        assertEquals("rest-client:QuoteClient", faults.target(QuoteClient.class).name());
    }

    @Test
    public void itsRequestsGoThroughTheProxy() {
        Target pricing = faults.target(QuoteClient.class);
        long before = pricing.traffic().httpRequestsSeen();
        assertEquals(PRICE, quotes.quote());
        assertEquals(1, pricing.traffic().httpRequestsSeen() - before, pricing.traffic().httpRequests().toString());
    }

    @Test
    public void theServiceIsUnavailable() {
        try (ActiveFault f = faults.target(QuoteClient.class).unavailable()) {
            assertThrows(RuntimeException.class, () -> quotes.quote());
        }
        assertEquals(PRICE, quotes.quote());
    }

    @Test
    public void aFaultOnOneOfItsMethods() {
        try (ActiveFault f = faults.target(QuoteClient::quote).http().respondsWith(503)) {
            assertThrows(RuntimeException.class, () -> quotes.quote());
        }
        assertEquals(PRICE, quotes.quote());
    }

    /**
     * The client built with the MicroProfile builder and a URL is a target of its own, and a fault on the other
     * built client leaves it alone.
     */
    @Test
    public void aClientOfTheMicroProfileBuilder() {
        Target rateTarget = faults.target(RateClient.class);
        assertEquals("rest-client:RateClient", rateTarget.name());
        try (ActiveFault f = faults.target(QuoteClient.class).unavailable()) {
            assertEquals(PRICE, rates.rate());
        }
        try (ActiveFault f = rateTarget.unavailable()) {
            assertThrows(RuntimeException.class, () -> rates.rate());
        }
        assertEquals(PRICE, rates.rate());
    }

    /**
     * The application's own listener still customises the client: its header is on the request the proxy saw.
     */
    @Test
    public void theApplicationsListenerStillApplies() {
        quotes.quote();
        List<HttpRequestRecord> requests = faults.target(QuoteClient.class).traffic().httpRequests();
        assertEquals("listener", requests.getLast().header(QuoteHeaderListener.HEADER), requests.toString());
    }

    /**
     * The registered clients pass through the same builder with the address of their proxy; they are not proxied
     * a second time. Nor are the two built clients, which every test of this class builds again.
     */
    @Test
    public void everyClientHasOneTarget() {
        when()
                .get("/orders/price")
                .then()
                .statusCode(200);
        Set<String> clients = new TreeSet<>();
        for (String name : faults.targetNames()) {
            if (name.startsWith("rest-client:")) {
                clients.add(name);
            }
        }
        assertEquals(Set.of("rest-client:PricingClient", "rest-client:QuoteClient", "rest-client:RateClient",
                "rest-client:export", "rest-client:inventory"), clients);
    }
}
