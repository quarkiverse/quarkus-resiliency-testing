package io.quarkiverse.resiliencytesting.restclient.runtime.internal;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.spi.RestClientListener;
import org.jboss.logging.Logger;

import io.quarkiverse.resiliencytesting.spi.ApplicationSuppliedClients;
import io.quarkiverse.resiliencytesting.spi.ReflectedField;
import io.quarkus.rest.client.reactive.runtime.RestClientBuilderImpl;

/**
 * Puts a proxy in front of a REST Client the application builds itself. The builder tells its listeners about every
 * client it is about to build, whichever of {@code QuarkusRestClientBuilder} and {@code RestClientBuilder} the
 * application used, once the base URI is settled and before it is used; a listener may still change it.
 * <p>
 * The listeners of the application are told about the same client, in the order the service loader finds them. One
 * that sets the base URI after this one has run decides where the client connects.
 */
public class ApplicationSuppliedClientListener implements RestClientListener {

    private static final Logger LOG = Logger.getLogger(ApplicationSuppliedClientListener.class);

    // The builder has setters for where the client connects but no getters, so both are read from its fields.
    private static final ReflectedField<URI> BASE_URI = ReflectedField.of(RestClientBuilderImpl.class, "uri", URI.class);
    private static final ReflectedField<String> DOMAIN_SOCKET_PATH = ReflectedField.of(RestClientBuilderImpl.class,
            "domainSocketPath", String.class);

    // the same failure for every client that is built, so it is said once
    private static final AtomicBoolean UNREADABLE = new AtomicBoolean();

    @Override
    public void onNewClient(Class<?> serviceInterface, RestClientBuilder builder) {
        if (!ApplicationSuppliedClients.enabled() || !(builder instanceof RestClientBuilderImpl)) {
            return;
        }
        URI baseUri;
        try {
            // a client over a Unix domain socket has a base URI too, a placeholder nothing connects to
            if (DOMAIN_SOCKET_PATH.read(builder) != null) {
                return;
            }
            baseUri = BASE_URI.read(builder);
        } catch (IllegalStateException e) {
            if (UNREADABLE.compareAndSet(false, true)) {
                LOG.errorf("%s REST Clients built in code, %s being the first, are not proxied and cannot be given "
                        + "faults; those whose URL is configured are unaffected.", e.getMessage(),
                        serviceInterface.getName());
            }
            return;
        }
        URI proxied = RestClientTargets.proxied(serviceInterface, baseUri);
        if (proxied != baseUri) {
            builder.baseUri(proxied);
        }
    }
}
