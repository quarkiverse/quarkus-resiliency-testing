package io.quarkiverse.resiliencytesting.it;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * The pricing service once more, for a client a test builds in code with the MicroProfile builder.
 */
@Path("/pricing")
public interface RateClient {

    @GET
    String rate();
}
