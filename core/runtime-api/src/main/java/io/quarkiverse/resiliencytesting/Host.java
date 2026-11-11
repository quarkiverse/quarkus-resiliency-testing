package io.quarkiverse.resiliencytesting;

/**
 * A host name the application looks up, from {@link Faults#host}.
 */
public interface Host {

    /**
     * {@code host:<name>}, the name in lower case.
     */
    String name();

    /**
     * The name does not resolve: every lookup of it fails with {@code UnknownHostException}, in the resolver of the
     * JDK, which {@code InetAddress}, sockets and JDBC drivers use, and in the one of Netty, which Vert.x and with
     * it the REST Client and the reactive clients use. Other names resolve, and connections already open stay open.
     * <p>
     * The fault is on the name, wherever in the JVM it is looked up, the test included: a test whose HTTP client
     * calls the application on {@code localhost} cannot make {@code localhost} unresolvable.
     * <p>
     * The test fails if nothing looked the name up while it was unresolvable.
     *
     * @throws IllegalStateException if this host is unresolvable already
     */
    ActiveFault unresolvable();
}
