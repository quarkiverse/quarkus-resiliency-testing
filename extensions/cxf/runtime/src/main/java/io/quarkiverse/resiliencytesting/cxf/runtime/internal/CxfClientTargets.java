package io.quarkiverse.resiliencytesting.cxf.runtime.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkiverse.cxf.CxfFixedConfig;
import io.quarkiverse.cxf.annotation.CXFClient;
import io.quarkiverse.resiliencytesting.spi.BeanTargetResolver;
import io.quarkiverse.resiliencytesting.spi.TargetBean;
import io.quarkiverse.resiliencytesting.spi.TargetDescriptor;
import io.quarkiverse.resiliencytesting.spi.TargetName;
import io.quarkiverse.resiliencytesting.spi.UrlRewriter;

/**
 * Network fault targets for Quarkus CXF SOAP clients. Each configured CXF client is identified by its client name,
 * and its interface under the {@code cxf} target family.
 */
public final class CxfClientTargets {

    private static final String FAMILY = "cxf";

    private CxfClientTargets() {
    }

    /**
     * Returns the target name associated with the given CXF client name.
     *
     * @param clientName the CXF client configuration name
     * @return the target name for the client
     */
    public static TargetName name(String clientName) {
        return TargetName.of(FAMILY, clientName);
    }

    /**
     * Describes the endpoint URL configuration property of CXF clients. The {@code {client}} placeholder matches
     * any configured client name, allowing its endpoint URL to be rewritten through a network fault proxy.
     *
     * @return the target descriptor for CXF client endpoint URLs
     */
    public static TargetDescriptor descriptor() {
        return new TargetDescriptor(
                "quarkus.cxf.client.{client}.client-endpoint-url",
                UrlRewriter.class,
                name("{client}"));
    }

    /**
     * Resolves a CXF client bean to its corresponding network fault target
     * using the {@link CXFClient} qualifier. Clients with an explicitly
     * provided configuration name and interface-only are supported.
     */
    @Singleton
    public static final class Resolver implements BeanTargetResolver {

        @Inject
        CxfFixedConfig config;

        @Override
        public Optional<TargetName> resolve(TargetBean bean) {
            Optional<CXFClient> qualifier = bean.qualifier(CXFClient.class);
            return resolveNamedClient(qualifier)
                    .or(() -> resolveByInterface(bean));
        }

        /**
         * Resolves a {@link CXFClient} by using its client key
         *
         * @param qualifier the object to be checked
         * @return an {@link Optional<TargetName>} that contains the client name
         */
        private Optional<TargetName> resolveNamedClient(Optional<CXFClient> qualifier) {
            return qualifier
                    .map(CXFClient::value)
                    .filter(value -> !value.isBlank())
                    .map(CxfClientTargets::name);
        }

        /**
         * Resolves a CXF client by its service interface, excluding alternative client configurations.
         *
         * @param bean the requested client bean
         * @return the target name if exactly one matching client exists
         * @throws IllegalArgumentException if multiple clients match
         */
        private Optional<TargetName> resolveByInterface(TargetBean bean) {
            List<String> matches = new ArrayList<>();

            for (var entry : config.clients().entrySet()) {
                if (entry.getValue().alternative()) {
                    continue;
                }

                String serviceInterface = entry.getValue()
                        .serviceInterface()
                        .orElse(null);

                if (serviceInterface == null) {
                    continue;
                }

                for (var type : bean.rawTypes()) {
                    if (type.getName().equals(serviceInterface)) {
                        matches.add(entry.getKey());
                        break;
                    }
                }
            }

            matches.sort(String::compareTo);

            if (matches.isEmpty()) {
                return Optional.empty();
            }

            if (matches.size() > 1) {
                throw new IllegalArgumentException(
                        "Multiple CXF clients match " + bean.rawTypes()
                                + ": " + matches
                                + ". Select a client with an explicit @CXFClient qualifier.");
            }

            return Optional.of(name(matches.getFirst()));
        }
    }
}
