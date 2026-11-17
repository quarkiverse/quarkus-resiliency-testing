package io.quarkiverse.resiliencytesting.restclient.runtime.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Optional;

import jakarta.inject.Singleton;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.quarkiverse.resiliencytesting.spi.ApplicationSuppliedClients;
import io.quarkiverse.resiliencytesting.spi.BeanTargetResolver;
import io.quarkiverse.resiliencytesting.spi.TargetBean;
import io.quarkiverse.resiliencytesting.spi.TargetDescriptor;
import io.quarkiverse.resiliencytesting.spi.TargetName;
import io.quarkiverse.resiliencytesting.spi.TargetRequests;
import io.quarkiverse.resiliencytesting.spi.UrlRewriter;

/**
 * REST Client targets, one per registered client, {@code rest-client:<configKey or simple name>}. A client's url may
 * be configured under its {@code configKey}, its class name or its simple name, and the build step names all three
 * the same, so whichever key the client resolves through reaches the same proxy. A client the application builds
 * itself has no such property: {@link ApplicationSuppliedClientListener} takes its base URI from the builder, under the name of
 * the interface it builds.
 */
public final class RestClientTargets {

    private static final String FAMILY = "rest-client";

    private RestClientTargets() {
    }

    /**
     * The name of the client with this config key, or simple class name without one.
     */
    public static TargetName name(String key) {
        return TargetName.of(FAMILY, key);
    }

    /**
     * The name of the client interface: its {@code configKey}, or its simple name without one.
     */
    public static TargetName name(Class<?> client) {
        RegisterRestClient annotation = client.getAnnotation(RegisterRestClient.class);
        String configKey = annotation == null ? null : annotation.configKey();
        return name(configKey == null || configKey.isBlank() ? client.getSimpleName() : configKey);
    }

    /**
     * The base URI a client of this interface is built with instead of the one its builder holds: that of the proxy
     * named after the interface. The very same URI when it already is that of a proxy, as for a registered client,
     * whose property was rewritten, or cannot be proxied.
     */
    static URI proxied(Class<?> client, URI baseUri) {
        String url = baseUri.toString();
        String proxied = ApplicationSuppliedClients.proxied(name(client), url, "the builder of the REST Client "
                + client.getSimpleName());
        return proxied.equals(url) ? baseUri : URI.create(proxied);
    }

    /**
     * The descriptor of the url or uri property of one config key, under the name of the client it belongs to.
     */
    public static TargetDescriptor descriptor(String key, String attribute, TargetName name) {
        return new TargetDescriptor(property(key, attribute), UrlRewriter.class, name);
    }

    /**
     * The property under which a key configures its url or uri, quoted when the key has dots.
     */
    public static String property(String key, String attribute) {
        return "quarkus.rest-client." + TargetDescriptor.propertySegment(key) + "." + attribute;
    }

    /**
     * A bean whose type is an interface annotated with {@code @RegisterRestClient} is that client, whether it was
     * asked for with the {@code @RestClient} qualifier or by the interface alone, and so is an interface the
     * application built a client of: one that has a target under its name. Which those are is asked of the
     * targets rather than remembered here, where it would outlive the application in dev mode. A method of the interface sends
     * the
     * requests its HTTP method annotation and its {@code @Path}, below the interface's own, say.
     */
    @Singleton
    public static final class Resolver implements BeanTargetResolver {
        @Override
        public Optional<TargetName> resolve(TargetBean bean) {
            Optional<Class<?>> client = bean.typeAnnotatedWith(RegisterRestClient.class);
            if (client.isPresent()) {
                return Optional.of(name(client.get()));
            }
            for (Class<?> type : bean.rawTypes()) {
                if (isBuilt(type)) {
                    return Optional.of(name(type));
                }
            }
            return Optional.empty();
        }

        @Override
        public Optional<TargetRequests> requests(Class<?> type, Method method) {
            if (!type.isAnnotationPresent(RegisterRestClient.class) && !isBuilt(type)) {
                return Optional.empty();
            }
            String httpMethod = httpMethod(method);
            if (httpMethod == null) {
                throw new IllegalArgumentException(type.getName() + "::" + method.getName() + " has no HTTP method "
                        + "annotation such as @GET or @POST, so it sends no request of its own; a sub-resource "
                        + "locator's requests are sent by the methods of the interface it returns.");
            }
            return Optional.of(new TargetRequests(name(type), httpMethod, path(type) + path(method)));
        }

        private static boolean isBuilt(Class<?> type) {
            return type.isInterface() && ApplicationSuppliedClients.hasTarget(name(type));
        }

        /**
         * The value of the annotation that is itself annotated with {@code @HttpMethod}: {@code @GET} and the like.
         */
        private static String httpMethod(Method method) {
            for (Annotation annotation : method.getAnnotations()) {
                HttpMethod httpMethod = annotation.annotationType().getAnnotation(HttpMethod.class);
                if (httpMethod != null) {
                    return httpMethod.value();
                }
            }
            return null;
        }

        /**
         * The {@code @Path} of an interface or a method as a path that starts with a slash and does not end with one,
         * or nothing without one.
         */
        private static String path(AnnotatedElement element) {
            Path path = element.getAnnotation(Path.class);
            if (path == null) {
                return "";
            }
            String value = path.value();
            if (value.endsWith("/")) {
                value = value.substring(0, value.length() - 1);
            }
            return value.isEmpty() || value.startsWith("/") ? value : "/" + value;
        }
    }
}
