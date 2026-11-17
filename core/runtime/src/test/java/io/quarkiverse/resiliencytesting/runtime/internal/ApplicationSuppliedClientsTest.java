package io.quarkiverse.resiliencytesting.runtime.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.resiliencytesting.runtime.internal.proxy.ProxyTarget;
import io.quarkiverse.resiliencytesting.spi.AddressUtil;
import io.quarkiverse.resiliencytesting.spi.ApplicationSuppliedClients;
import io.quarkiverse.resiliencytesting.spi.TargetName;

/**
 * The address of a built client, as {@link ApplicationSuppliedClients#proxied} answers it once the core has provided its
 * proxies.
 */
class ApplicationSuppliedClientsTest {

    private static final String HOLDER = "a builder";

    @BeforeEach
    void provide() {
        FaultsRuntime.proxyApplicationSuppliedClients();
    }

    @AfterEach
    void close() {
        ApplicationSuppliedClients.proxies(null);
        FaultsRuntime.close();
    }

    private static ProxyTarget only() {
        assertEquals(1, FaultsRuntime.registry().targets().size(), FaultsRuntime.registry().targets().toString());
        return FaultsRuntime.registry().targets().iterator().next();
    }

    @Test
    void aUrlPointsAtAProxyUnderTheGivenName() {
        String proxied = ApplicationSuppliedClients.proxied(TargetName.of("mcp", "tides"), "http://tides.example:8080/mcp?x=1",
                HOLDER);

        ProxyTarget target = only();
        assertEquals("mcp:tides", target.name());
        assertEquals("tides.example", target.upstream().host());
        assertEquals(8080, target.upstream().port());
        assertEquals(HOLDER, target.sourceProperty());
        assertEquals("http://" + AddressUtil.PROXY_HOST + ":" + target.port() + "/mcp?x=1", proxied);
    }

    @Test
    void aUrlThatAlreadyPointsAtAProxyIsLeftAlone() {
        String proxied = ApplicationSuppliedClients.proxied(TargetName.of("mcp", "tides"), "http://tides.example:8080/mcp",
                HOLDER);

        assertEquals(proxied, ApplicationSuppliedClients.proxied(TargetName.of("mcp", "other"), proxied, HOLDER));
        assertEquals("mcp:tides", only().name());
    }

    @Test
    void whatIsNotAUrlIsLeftAlone() {
        assertEquals("/relative", ApplicationSuppliedClients.proxied(TargetName.of("mcp", "tides"), "/relative", HOLDER));
        assertNull(ApplicationSuppliedClients.proxied(TargetName.of("mcp", "tides"), null, HOLDER));
        assertTrue(FaultsRuntime.registry().targets().isEmpty());
    }

    @Test
    void withoutProxiesTheUrlIsLeftAlone() {
        ApplicationSuppliedClients.proxies(null);
        String url = "http://tides.example:8080/mcp";

        assertEquals(url, ApplicationSuppliedClients.proxied(TargetName.of("mcp", "tides"), url, HOLDER));
    }
}
