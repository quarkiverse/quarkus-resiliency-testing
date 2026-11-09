package io.quarkiverse.resiliencytesting.kafka;

import io.quarkiverse.resiliencytesting.junit.NetworkFaultsProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * First half of the restart test pair (see {@link RestartBFaultsTest}).
 * Runs with the standard {@link NetworkFaultsProfile}; {@code RestartBFaultsTest} adds a class-scoped
 * {@code @WithTestResource} that forces Quarkus to restart while reusing the same Kafka DevServices container,
 * exercising the proxy-port recovery path (issue #19).
 */
@QuarkusTest
@TestProfile(NetworkFaultsProfile.class)
public class RestartAFaultsTest extends AbstractKafkaFaultsTest {
}
