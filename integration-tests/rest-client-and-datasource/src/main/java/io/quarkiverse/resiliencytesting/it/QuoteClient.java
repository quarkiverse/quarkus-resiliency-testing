package io.quarkiverse.resiliencytesting.it;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * The pricing service again, for a client a test builds in code: no {@code @RegisterRestClient} and no
 * configuration, so its address is only known to the builder.
 */
@Path("/pricing")
public interface QuoteClient {

    @GET
    String quote();
}
