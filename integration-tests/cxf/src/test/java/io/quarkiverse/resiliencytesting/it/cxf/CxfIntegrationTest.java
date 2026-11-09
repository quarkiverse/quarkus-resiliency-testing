package io.quarkiverse.resiliencytesting.it.cxf;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import jakarta.inject.Inject;
import jakarta.xml.ws.WebServiceException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.resiliencytesting.Faults;
import io.quarkiverse.resiliencytesting.it.cxf.util.CXFClientLiteral;
import io.quarkiverse.resiliencytesting.junit.NetworkFaultsProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(NetworkFaultsProfile.class)
class CxfIntegrationTest {

    @Inject
    Faults faults;

    @Inject
    SoapCaller soapCaller;

    @Test
    @DisplayName("Should communicate with CXF Client when no faults are applied")
    void shouldCommunicateWithCXFClientNormallyWhenNoFaultsAreApplied() {
        // given: a name
        String name = "Alice";

        // when: calling the client
        String result = soapCaller.callPayments(name);

        // then: the client should return the result without errors applied
        assertEquals("Hello Alice", result);
    }

    @Test
    @DisplayName("Should register the CXF Client targets")
    void shouldRegisterTheCXFClientTargets() {
        // given: the expected targets
        Set<String> expectedTargets = Set.of(
                "cxf:orders",
                "cxf:payments",
                "cxf:default-endpoint",
                "cxf:receipts",
                "cxf:inferred-interface",
                "cxf:inferred-interface-default-endpoint",
                "cxf:noname");

        // when: retrieving the registered targets
        Set<String> actualTargets = faults.targetNames();

        // then: the CXF clients should be registered
        assertEquals(expectedTargets, actualTargets);
    }

    @Test
    @DisplayName("Should route SOAP traffic through the network proxy")
    void shouldRouteSoapTrafficThroughProxy() {
        // given: a configured CXF SOAP client
        String name = "Alice";

        // when: invoking the SOAP service
        String result = soapCaller.callPayments(name);

        // then: the request should succeed
        assertEquals("Hello Alice", result);

        // and: the proxy should have observed network connections
        assertTrue(
                faults.all().traffic().connectionsSeen() > 0,
                "No network connections were observed by the proxy");
    }

    @Test
    @DisplayName("Should inject a network fault to CXF client using its service interface")
    void shouldInjectNetworkFaultToCxfClientUsingServiceInterface() {
        // when: applying a hang fault by interface alone
        try (var fault = faults.target(SoapService.class).hangs()) {

            // then: payments should fail
            assertThrows(
                    WebServiceException.class,
                    () -> soapCaller.callPayments("Bob"));

            // and: orders should remain operational
            assertEquals("Hello Alice", soapCaller.callOrders("Alice"));
        }

        // and: payments should recover
        assertEquals("Hello Carol", soapCaller.callPayments("Carol"));
    }

    @Test
    @DisplayName("Should inject a network fault to CXF client using its client name")
    void shouldInjectNetworkFaultToCxfClientUsingClientName() {
        // given: a configured CXF SOAP client
        var qualifier = new CXFClientLiteral("orders");

        // and: both clients are initially operational
        assertEquals("Hello Alice", soapCaller.callOrders("Alice"));
        assertEquals("Hello Bob", soapCaller.callPayments("Bob"));

        // when: applying a hang fault to orders
        try (var fault = faults.target(SoapService.class, qualifier).hangs()) {

            // then: orders should fail
            assertThrows(
                    WebServiceException.class,
                    () -> soapCaller.callOrders("Charlie"));

            // but: payments should remain operational
            assertEquals("Hello David", soapCaller.callPayments("David"));
        }

        // and: orders should recover
        assertEquals("Hello Eve", soapCaller.callOrders("Eve"));
    }

    @Test
    @DisplayName("Should inject network faults into two CXF clients simultaneously with different URLs")
    void shouldInjectNetworkFaultsIntoTwoCxfClientsSimultaneouslyWithDifferentURLs() {
        // given: two separately configured CXF clients
        var ordersQualifier = new CXFClientLiteral("orders");
        var receiptsQualifier = new CXFClientLiteral("receipts");

        // when: applying a hang fault to both clients
        try (var ordersFault = faults.target(
                SoapService.class, ordersQualifier).hangs();

                var receiptsFault = faults.target(
                        SoapService.class, receiptsQualifier).hangs()) {

            // then: the orders client should fail
            assertThrows(
                    WebServiceException.class,
                    () -> soapCaller.callOrders("Alice"));

            // and: the receipts client should also fail
            assertThrows(
                    WebServiceException.class,
                    () -> soapCaller.callReceipts("Bob"));

            // but: the payments client should remain operational
            assertEquals(
                    "Hello Charlie",
                    soapCaller.callPayments("Charlie"));
        }

        // and: both affected clients should recover after the faults end
        assertEquals("Hello David", soapCaller.callOrders("David"));
        assertEquals("Hello Eve", soapCaller.callReceipts("Eve"));
    }

    @Test
    @DisplayName("Should inject a network fault when CXF service interface is omitted")
    void shouldInjectFaultWhenCxfServiceInterfaceIsOmitted() {
        // given: a CXF client without service-interface configuration
        var qualifier = new CXFClientLiteral("inferred-interface");

        // when: applying a network fault
        try (var fault = faults.target(SoapService.class, qualifier).hangs()) {

            // then: the client should fail
            assertThrows(
                    WebServiceException.class,
                    () -> soapCaller.callInferred("Bob"));
        }

        // and: communication should recover
        assertEquals("Hello Carol", soapCaller.callInferred("Carol"));
    }
}
