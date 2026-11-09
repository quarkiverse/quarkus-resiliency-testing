package io.quarkiverse.resiliencytesting.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.resiliencytesting.junit.NetworkFaultsProfile;
import io.quarkiverse.resiliencytesting.runtime.internal.proxy.ProxyRegistry;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Second half of the restart test pair (see {@link RestartAFaultsTest}).
 * The class-scoped {@code @WithTestResource} forces Quarkus to restart the application while the Kafka DevServices
 * container is kept. After the restart the proxy must re-bind to the same port the broker advertises (issue #19).
 */
@QuarkusTest
@TestProfile(NetworkFaultsProfile.class)
@WithTestResource(RestartBFaultsTest.Noop.class)
public class RestartBFaultsTest extends AbstractKafkaFaultsTest {

    @Test
    public void applicationRestartedAndPreviousProxyClosed() {
        assertEquals(1, ProxyRegistry.liveInstances(), "one proxy Vert.x instance per application");
    }

    public static class Noop implements QuarkusTestResourceLifecycleManager {
        @Override
        public Map<String, String> start() {
            return Map.of();
        }

        @Override
        public void stop() {
        }
    }
}
