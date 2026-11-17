package io.quarkiverse.resiliencytesting.test;

import java.net.URI;
import java.util.TreeSet;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;

import io.quarkiverse.resiliencytesting.ActiveFault;
import io.quarkiverse.resiliencytesting.Faults;
import io.quarkiverse.resiliencytesting.runtime.internal.proxy.ProxyRegistry;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * An application that builds a REST Client in code, to itself, and lets a dev mode test see what became of it:
 * {@code /upstream} is what the client calls, and the routes under {@code /supplied} call it, apply a fault to it and
 * list the targets.
 */
@Singleton
public class ApplicationSuppliedClientRoutes {

    static final String ANSWER = "pong";

    private final Faults faults;
    private PingClient client;

    public ApplicationSuppliedClientRoutes(Faults faults) {
        this.faults = faults;
    }

    void routes(@Observes Router router) {
        router.get("/upstream").handler(new Upstream());
        router.get("/supplied/:what").blockingHandler(new Supplied());
    }

    private synchronized PingClient client() {
        if (client == null) {
            client = QuarkusRestClientBuilder.newBuilder()
                    .baseUri(URI.create("http://localhost:8080"))
                    .build(PingClient.class);
        }
        return client;
    }

    private static final class Upstream implements Handler<RoutingContext> {

        @Override
        public void handle(RoutingContext context) {
            context.response().end(ANSWER);
        }
    }

    private final class Supplied implements Handler<RoutingContext> {

        @Override
        public void handle(RoutingContext context) {
            context.response().end(answer(context.pathParam("what")));
        }

        private String answer(String what) {
            return switch (what) {
                case "call" -> client().ping();
                case "targets" -> String.join(",", new TreeSet<>(faults.targetNames()));
                case "registries" -> Integer.toString(ProxyRegistry.liveInstances());
                case "down" -> down();
                default -> "unknown";
            };
        }

        /**
         * Whether the call fails while the target of the client is unavailable.
         */
        private String down() {
            PingClient pings = client();
            try (ActiveFault f = faults.target(PingClient.class).unavailable()) {
                pings.ping();
                return "answered";
            } catch (RuntimeException e) {
                return "failed";
            }
        }
    }
}
