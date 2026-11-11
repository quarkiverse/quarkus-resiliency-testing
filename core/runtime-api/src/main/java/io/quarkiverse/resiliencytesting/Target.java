package io.quarkiverse.resiliencytesting;

/**
 * One outbound dependency of the application, fronted by a proxy. Faults are applied to the proxy and act on
 * everything that goes through it; besides the faults of every {@link Endpoint}, a target takes those that act on
 * connections rather than on requests.
 * <p>
 * A fault on a target has it to itself: applying one while any fault is active on it, on some of its requests or on
 * any of the connections it is made of, throws {@link IllegalStateException}, and so does applying a fault to some of
 * its requests while it has one. A target whose pool is exhausted, through {@link Faults#pool}, takes no
 * network fault and no {@link #dropConnections()}, which throw {@link IllegalStateException} too. So does a network
 * fault on a target that is {@link #unresolvable()}: nothing connects to it for the fault to act on.
 */
public interface Target extends Endpoint {

    /**
     * The registry name, {@code family:segment:segment}; a target made of several connections answers with the
     * name that covers them.
     */
    String name();

    /**
     * Connections are refused.
     */
    ActiveFault unavailable();

    /**
     * Connection attempts hang until the client's connect timeout. Linux only: the listener's accept queue is left
     * full, so the kernel drops the SYNs.
     */
    ActiveFault connectTimeout();

    /**
     * The host name of the target does not resolve: every attempt to connect to it fails with
     * {@code UnknownHostException}, naming the host the application was configured with, before a connection is made.
     * Connections already open stay open, so a pooled client meets the fault once it needs a new one;
     * {@link #dropConnections()} first makes that the next request.
     * <p>
     * The application reaches a target through its proxy and looks no name up, so the failure is raised where the
     * client would connect: in the socket channels of {@code java.nio}, which Netty and Vert.x use, and in the
     * sockets of {@code java.net}, which JDBC drivers use. A client on a native transport, epoll or io_uring, is not
     * failed. {@link Faults#host} fails the lookups themselves, for a name the application does look up.
     * <p>
     * The test fails if nothing tried to connect to the target while it was unresolvable.
     *
     * @throws IllegalStateException if the target is unresolvable already, has an active network fault, or its pool
     *         is exhausted
     */
    ActiveFault unresolvable();

    /**
     * Connections stay open but nothing is relayed, until the client's read timeout.
     */
    @Override
    ActiveFault hangs();

    /**
     * Reset every live connection now: pooled connections come back broken. One-shot, not a fault that stays active.
     */
    void dropConnections();

    /**
     * What went through the proxy: live connections, and the connections and HTTP requests seen so far.
     */
    @Override
    TargetTraffic traffic();
}
