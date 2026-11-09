package io.quarkiverse.resiliencytesting.cxf.deployment;

import java.util.HashMap;
import java.util.Map;

import io.quarkiverse.cxf.CxfFixedConfig;
import io.quarkiverse.cxf.deployment.CxfClientProcessor;
import io.quarkiverse.resiliencytesting.cxf.runtime.internal.CxfClientTargets;
import io.quarkiverse.resiliencytesting.deployment.spi.NetworkFaultDefaultValueBuildItem;
import io.quarkiverse.resiliencytesting.deployment.spi.NetworkFaultTargetBuildItem;
import io.quarkiverse.resiliencytesting.deployment.spi.NetworkFaultsEnabled;
import io.quarkiverse.resiliencytesting.spi.TargetDescriptor;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;

/**
 * Registers network fault targets for Quarkus CXF SOAP clients. CXF client endpoint URLs are matched dynamically
 * using a configuration property pattern, without requiring individual client discovery.
 */
@BuildSteps(onlyIf = NetworkFaultsEnabled.class, onlyIfNot = IsNormal.class)
public class CxfClientTargetsProcessor {

    private static final String DEFAULT_EP_ADDR = "http://localhost:8080";

    /**
     * Registers the CXF client endpoint URL descriptor for network fault injection.
     */
    @BuildStep
    void targets(BuildProducer<NetworkFaultTargetBuildItem> targets) {
        targets.produce(new NetworkFaultTargetBuildItem(CxfClientTargets.descriptor()));
    }

    /**
     * Registers default endpoint URLs for configured CXF clients
     * whose service interfaces can be resolved.
     * <p>
     * Explicitly configured endpoint URLs take precedence over
     * the generated defaults.
     */
    @BuildStep
    void defaultEndpointUrls(
            CxfFixedConfig config,
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<NetworkFaultDefaultValueBuildItem> defaults) {

        Map<String, String> serviceInterfaces = collectConfiguredServiceInterfaces(config);

        discoverServiceInterfaces(combinedIndex, serviceInterfaces);

        registerDefaultEndpointUrls(config, serviceInterfaces, defaults);
    }

    /**
     * Collects explicitly configured service interfaces, indexed by their CXF client configuration names.
     *
     * <p>
     * For example, given:
     *
     * <pre>{@code
     * quarkus.cxf.client.orders.service-interface=com.example.SoapService
     * }</pre>
     * <p>
     * The resulting map contains:
     *
     * <pre>{@code
     * clientName -> com.example.SoapService
     * }</pre>
     *
     * @param config the CXF client configuration
     * @return a map of client names to service interface class names
     */
    private static Map<String, String> collectConfiguredServiceInterfaces(CxfFixedConfig config) {
        Map<String, String> serviceInterfaces = new HashMap<>();
        config.clients().forEach((clientName, clientConfig) -> clientConfig.serviceInterface()
                .ifPresent(sei -> serviceInterfaces.put(clientName, sei)));
        return serviceInterfaces;
    }

    /**
     * Discovers service interfaces from named {@code @CXFClient} injection points when they are not
     * explicitly configured.
     *
     * <p>
     * For example, given:
     *
     * <pre>{@code
     * @CXFClient("client-name")
     * SoapService ordersClient;
     * }</pre>
     *
     * <p>
     * and when the next property is absent:
     *
     * <pre>{@code
     * quarkus.cxf.client."client-name".service-interface
     * }</pre>
     * <p>
     * The method adds the following mapping:
     *
     * <pre>{@code
     * clientName -> com.example.SoapService
     * }</pre>
     * <p>
     * Explicitly configured service interfaces take precedence over those inferred from injection points.
     *
     * @param combinedIndex the application index containing injection points
     * @param serviceInterfaces the map to populate with discovered interfaces
     */
    private static void discoverServiceInterfaces(
            CombinedIndexBuildItem combinedIndex,
            Map<String, String> serviceInterfaces) {
        CxfClientProcessor
                .findClientInjectionPoints(combinedIndex.getIndex())
                .forEach(injectionPoint -> {
                    String clientName = injectionPoint.getConfigKey();

                    if (!clientName.isBlank()) {
                        serviceInterfaces.putIfAbsent(
                                clientName,
                                injectionPoint.getSei().getName());
                    }
                });
    }

    /**
     * Registers default endpoint URLs for CXF clients with a resolved service interface.
     *
     * <p>
     * For example, if a client declares:
     *
     * <pre>{@code
     * quarkus.cxf.client."client-name".service-interface=com.example.SoapService
     * }</pre>
     * <p>
     * But omits:
     *
     * <pre>{@code
     * quarkus.cxf.client."client-name".client-endpoint-url
     * }</pre>
     * <p>
     * The method registers the following default:
     *
     * <pre>{@code
     * quarkus.cxf.client."client-name".client-endpoint-url=
     *     http://localhost:8080/com.example.soapservice
     * }</pre>
     * <p>
     * The default matches the fallback endpoint address calculated by Quarkus CXF. Explicitly
     * configured endpoint URLs take precedence over generated defaults. Clients without a resolvable
     * service interface are skipped.
     *
     * @param config the CXF client configuration
     * @param serviceInterfaces the resolved service interfaces by client name
     * @param defaults the producer of default endpoint URL build items
     */
    private static void registerDefaultEndpointUrls(
            CxfFixedConfig config,
            Map<String, String> serviceInterfaces,
            BuildProducer<NetworkFaultDefaultValueBuildItem> defaults) {

        config.clients().keySet().forEach(clientName -> {
            String sei = serviceInterfaces.get(clientName);

            if (sei == null) {
                return;
            }

            String property = "quarkus.cxf.client."
                    + TargetDescriptor.propertySegment(clientName)
                    + ".client-endpoint-url";

            String defaultUrl = DEFAULT_EP_ADDR + "/" + sei.toLowerCase();

            defaults.produce(new NetworkFaultDefaultValueBuildItem(property, defaultUrl));
        });
    }
}
