package io.quarkiverse.resiliencytesting.test;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * Not registered with the REST Client extension and not configured: the application builds a client of it in code.
 */
@Path("/upstream")
public interface PingClient {

    @GET
    String ping();
}
