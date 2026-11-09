package io.quarkiverse.resiliencytesting.it.cxf.util;

import java.util.HashMap;
import java.util.Map;

import io.quarkiverse.resiliencytesting.it.cxf.DefaultSoapEndpoint;
import io.quarkiverse.resiliencytesting.junit.NetworkFaultsProfile;

/**
 * Test profile for verifying network fault injection into CXF clients
 * that do not explicitly configure a client endpoint URL.
 * <p>
 * Configures the test application to expose a SOAP endpoint at the
 * default address calculated by Quarkus CXF.
 */
public class CxfDefaultEndpointProfile extends NetworkFaultsProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        var config = new HashMap<>(super.getConfigOverrides());

        config.put("quarkus.http.test-port", "8080");
        config.put("quarkus.cxf.path", "/");
        config.put("quarkus.cxf.client.payments.alternative", "true");
        config.put("quarkus.cxf.client.noname.alternative", "false");

        config.put(
                "quarkus.cxf.endpoint.\"/io.quarkiverse.resiliencytesting.it.cxf.soapservice\".implementor",
                DefaultSoapEndpoint.class.getName());

        return config;
    }
}
