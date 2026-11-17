package io.quarkiverse.resiliencytesting.it;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.spi.RestClientListener;

/**
 * A listener of the application's own, which customises the quote client as it is built: it has to keep working
 * next to the one that puts a proxy in front of the client.
 */
public class QuoteHeaderListener implements RestClientListener {

    public static final String HEADER = "X-Quoted-By";

    @Override
    public void onNewClient(Class<?> serviceInterface, RestClientBuilder builder) {
        if (serviceInterface == QuoteClient.class) {
            builder.register(new QuotedBy());
        }
    }

    private static final class QuotedBy implements ClientRequestFilter {

        @Override
        public void filter(ClientRequestContext request) {
            request.getHeaders().add(HEADER, "listener");
        }
    }
}
