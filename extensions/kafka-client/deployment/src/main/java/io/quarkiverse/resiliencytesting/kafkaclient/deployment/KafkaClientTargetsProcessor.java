package io.quarkiverse.resiliencytesting.kafkaclient.deployment;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.jboss.logging.Logger;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

import io.quarkiverse.resiliencytesting.deployment.spi.NetworkFaultTargetBuildItem;
import io.quarkiverse.resiliencytesting.deployment.spi.NetworkFaultsEnabled;
import io.quarkiverse.resiliencytesting.kafkaclient.runtime.internal.KafkaClientTargets;
import io.quarkiverse.resiliencytesting.spi.AddressUtil;
import io.quarkiverse.resiliencytesting.spi.TargetDescriptor;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.DevServicesAdditionalConfigBuildItem;
import io.quarkus.deployment.builditem.DevServicesCustomizerBuildItem;
import io.quarkus.deployment.builditem.Startable;
import io.quarkus.devservices.common.StartableContainer;

@BuildSteps(onlyIf = NetworkFaultsEnabled.class, onlyIfNot = IsNormal.class)
class KafkaClientTargetsProcessor {

    private static final Logger LOG = Logger.getLogger(KafkaClientTargetsProcessor.class);

    private static final String PROXY_PORT_LABEL = "quarkus.resiliency-testing.kafka.proxy-port";

    @BuildStep
    NetworkFaultTargetBuildItem target(BuildProducer<DevServicesCustomizerBuildItem> customizers,
            BuildProducer<DevServicesAdditionalConfigBuildItem> additionalConfig) {
        AtomicInteger proxyPort = new AtomicInteger();

        customizers.produce(new DevServicesCustomizerBuildItem((devService, startable) -> {
            if (!"kafka-client".equals(devService.getName())) {
                return startable;
            }
            GenericContainer<?> container = unwrapContainer(startable);
            if (container == null) {
                return startable;
            }

            int port = AddressUtil.reservePort();
            proxyPort.set(port);
            container.withLabel(PROXY_PORT_LABEL, Integer.toString(port));
            LOG.infof("network faults: reserved proxy port %d for Kafka broker advertised.listeners", port);

            return new KafkaProxyStartable(startable, container, port);
        }));

        String property = KafkaClientTargets.DESCRIPTOR.property();
        additionalConfig.produce(new DevServicesAdditionalConfigBuildItem(devServicesConfig -> {
            int port = proxyPort.get();
            if (port == 0) {
                String bootstrapServers = devServicesConfig.get(property);
                if (bootstrapServers != null) {
                    port = proxyPortFromContainerLabel(bootstrapServers);
                }
            }
            if (port > 0) {
                return Map.of(TargetDescriptor.RESERVED_PORT_PREFIX + property,
                        Integer.toString(port));
            }
            return Map.of();
        }));
        return new NetworkFaultTargetBuildItem(KafkaClientTargets.DESCRIPTOR);
    }

    private static int parsePort(String address) {
        String authority = address;
        int schemeEnd = authority.indexOf("://");
        if (schemeEnd >= 0) {
            authority = authority.substring(schemeEnd + 3);
        }
        AddressUtil.HostPort hp = AddressUtil.hostPort(authority, -1);
        if (hp == null) {
            return 0;
        }
        return hp.port();
    }

    private static int proxyPortFromContainerLabel(String bootstrapServers) {
        var containers = DockerClientFactory.lazyClient()
                .listContainersCmd()
                .withLabelFilter(List.of(PROXY_PORT_LABEL))
                .exec();
        if (containers.isEmpty()) {
            return 0;
        }
        for (String server : bootstrapServers.split(",")) {
            int kafkaPort = parsePort(server.trim());
            if (kafkaPort <= 0) {
                continue;
            }
            for (var container : containers) {
                boolean matchesKafkaPort = container.getPorts() != null &&
                        Arrays.stream(container.getPorts()).anyMatch(p -> Objects.equals(p.getPublicPort(), kafkaPort));
                if (!matchesKafkaPort) {
                    continue;
                }
                String value = container.getLabels().get(PROXY_PORT_LABEL);
                if (value != null) {
                    try {
                        int port = Integer.parseInt(value);
                        LOG.infof("network faults: reusing proxy port %d for restarted application (Kafka container kept)",
                                port);
                        return port;
                    } catch (NumberFormatException e) {
                        LOG.warnf("network faults: ignoring invalid proxy port label value '%s'", value);
                    }
                }
            }
        }
        LOG.debugf("network faults: no running container with label %s matches bootstrap servers %s",
                PROXY_PORT_LABEL, bootstrapServers);
        return 0;
    }

    private static GenericContainer<?> unwrapContainer(Startable startable) {
        if (startable instanceof StartableContainer<?> sc) {
            return sc.getContainer();
        }
        if (startable instanceof GenericContainer<?> gc) {
            return gc;
        }
        return null;
    }

    enum KafkaProvider {
        UPSTREAM_KAFKA("/tmp/testcontainers_start.sh", false),
        REDPANDA("/var/lib/redpanda/redpanda.sh", true),
        STRIMZI(null, false);

        final String script;
        final boolean shEntrypoint;

        KafkaProvider(String script, boolean shEntrypoint) {
            this.script = script;
            this.shEntrypoint = shEntrypoint;
        }
    }

    static KafkaProvider resolveProvider(String imageName) {
        if (imageName.contains("redpanda")) {
            return KafkaProvider.REDPANDA;
        } else if (imageName.contains("strimzi")) {
            return KafkaProvider.STRIMZI;
        }
        return KafkaProvider.UPSTREAM_KAFKA;
    }

    static String sedExprFor(KafkaProvider provider, int port) {
        return switch (provider) {
            case REDPANDA -> "s|(--advertise-kafka-addr .*)OUTSIDE://[^ ]*|\\1OUTSIDE://localhost:" + port + "|";
            case UPSTREAM_KAFKA -> "s|PLAINTEXT://[^,]*|PLAINTEXT://localhost:" + port + "|";
            default -> throw new IllegalArgumentException("no sed expression for " + provider);
        };
    }

    /**
     * Wraps the Kafka {@link Startable} to make the broker advertise the proxy port. Each container image
     * configures advertised listeners differently; the wrapper detects the image and patches the startup
     * script or configuration file accordingly.
     */
    private static final class KafkaProxyStartable implements Startable {

        private static final String PATCHED_SCRIPT = "/tmp/nf_patched_start.sh";
        private static final int KAFKA_PORT = 9092;

        private final Startable delegate;
        private final GenericContainer<?> container;
        private final int proxyPort;
        private String actualConnectionInfo;

        KafkaProxyStartable(Startable delegate, GenericContainer<?> container, int proxyPort) {
            this.delegate = delegate;
            this.container = container;
            this.proxyPort = proxyPort;
        }

        @Override
        public void start() {
            KafkaProvider provider = resolveProvider(container.getDockerImageName());
            if (provider == KafkaProvider.STRIMZI) {
                patchStrimzi();
            } else {
                patchScript(provider);
            }
            delegate.start();
            if (provider == KafkaProvider.STRIMZI) {
                actualConnectionInfo = "PLAINTEXT://localhost:" + container.getMappedPort(KAFKA_PORT);
            }
            LOG.infof("network faults: Kafka broker started (connection info: %s, proxy port: %d)",
                    getConnectionInfo(), proxyPort);
        }

        private void patchScript(KafkaProvider provider) {
            String sedExpr = sedExprFor(provider, proxyPort);
            String sedFlags = provider == KafkaProvider.REDPANDA ? "-E" : "";
            String cmd = String.format(
                    "while [ ! -f %1$s ]; do sleep 0.1; done; sleep 0.1; "
                            + "sed %4$s '%2$s' %1$s > %3$s; chmod +x %3$s; "
                            + "%3$s",
                    provider.script, sedExpr, PATCHED_SCRIPT, sedFlags);
            if (provider.shEntrypoint) {
                container.withCommand("-c", cmd);
            } else {
                container.withCommand("sh", "-c", cmd);
            }
        }

        @SuppressWarnings("unchecked")
        private void patchStrimzi() {
            try {
                Field f = container.getClass().getDeclaredField("bootstrapServersProvider");
                f.setAccessible(true);
                f.set(container, (Function<Object, String>) c -> "PLAINTEXT://localhost:" + proxyPort);
            } catch (ReflectiveOperationException e) {
                LOG.warnf("network faults: could not configure Strimzi advertised listeners: %s", e.getMessage());
            }
        }

        @Override
        public String getConnectionInfo() {
            return actualConnectionInfo != null ? actualConnectionInfo : delegate.getConnectionInfo();
        }

        @Override
        public String getContainerId() {
            return delegate.getContainerId();
        }

        @Override
        public boolean isReusable() {
            return delegate.isReusable();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
