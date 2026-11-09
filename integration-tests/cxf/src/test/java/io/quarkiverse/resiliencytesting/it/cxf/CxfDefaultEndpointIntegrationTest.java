package io.quarkiverse.resiliencytesting.it.cxf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.inject.Inject;
import jakarta.xml.ws.WebServiceException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.resiliencytesting.Faults;
import io.quarkiverse.resiliencytesting.it.cxf.util.CXFClientLiteral;
import io.quarkiverse.resiliencytesting.it.cxf.util.CxfDefaultEndpointProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(CxfDefaultEndpointProfile.class)
public class CxfDefaultEndpointIntegrationTest {

    @Inject
    Faults faults;

    @Inject
    SoapCaller soapCaller;

    @Test
    @DisplayName("Should inject network fault when CXF endpoint URL is omitted")
    void shouldInjectFaultWhenCxfEndpointUrlIsOmitted() {
        // given: a configured CXF SOAP client
        var ordersQualifier = new CXFClientLiteral("default-endpoint");

        // when: applying a hang fault to the client
        try (var fault = faults.target(SoapService.class, ordersQualifier).hangs()) {

            // then: the client should fail
            assertThrows(
                    WebServiceException.class,
                    () -> soapCaller.callFallback("Alice"));
        }

        // and: communication should recover after the fault ends
        assertEquals("Hello David", soapCaller.callFallback("David"));
    }

    @Test
    @DisplayName("Should inject a network fault into an unnamed CXF client with omitted endpoint URL")
    void shouldInjectFaultIntoUnnamedCxfClientWithOmittedEndpointUrl() {
        // when: applying a hang fault to the unnamed client
        try (var fault = faults.target(SoapService.class).hangs()) {

            // then: the client should fail
            assertThrows(
                    WebServiceException.class,
                    () -> soapCaller.callNoName("Alice"));
        }

        // and: communication should recover after the fault ends
        assertEquals("Hello David", soapCaller.callNoName("David"));
    }

    @Test
    @DisplayName("Should inject network fault when CXF interface class and endpoint URL are omitted")
    void shouldInjectFaultWhenCxfInterfaceAndEndpointUrlAreOmitted() {
        // given: a configured CXF SOAP client
        var interfaceQualifier = new CXFClientLiteral("inferred-interface-default-endpoint");

        // when: applying a hang fault to the client
        try (var fault = faults.target(SoapService.class, interfaceQualifier).hangs()) {

            // then: the client should fail
            assertThrows(
                    WebServiceException.class,
                    () -> soapCaller.callInterface("Alice"));
        }

        // and: communication should recover after the fault ends
        assertEquals("Hello David", soapCaller.callInterface("David"));
    }
}
