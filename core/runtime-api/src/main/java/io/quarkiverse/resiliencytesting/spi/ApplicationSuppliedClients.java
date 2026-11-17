package io.quarkiverse.resiliencytesting.spi;

/**
 * The clients the application builds itself rather than configures: their address is in no configuration property a
 * {@link TargetDescriptor} could name, only in the builder the application hands it to, and then in the client. The
 * module that knows the client reads the address from where it is, passes it through {@link #proxied} and puts the
 * answer back, so that the client connects to a proxy from then on.
 */
public final class ApplicationSuppliedClients {

    /**
     * What stands a proxy in front of an address: the core of this extension, which provides it at start.
     */
    public interface Proxies {

        /**
         * The port of the proxy standing in for {@code upstream}, created on first sight under {@code name}.
         *
         * @param holder what holds the address, shown where a configured target shows its property
         */
        int proxyPort(TargetName name, Upstream upstream, String holder);

        /**
         * Whether a target with this name exists by now.
         */
        boolean hasTarget(TargetName name);
    }

    private static final UrlRewriter URLS = new UrlRewriter();

    private static volatile Proxies proxies;

    private ApplicationSuppliedClients() {
    }

    /**
     * Provided when the application starts and taken away, with null, when it stops: this class outlives the
     * application in dev mode, and must not keep what belonged to it.
     */
    public static void proxies(Proxies provided) {
        proxies = provided;
    }

    /**
     * Whether addresses are proxied at all: false when fault injection is off, so that nothing needs to be read.
     */
    public static boolean enabled() {
        return proxies != null;
    }

    /**
     * The URL with its address replaced by that of a proxy, which is the target {@code name}; the URL itself when
     * it is not something that can be proxied, or already points at a proxy, as the URL of a configured client
     * does: no second target is made for it.
     *
     * @param holder what holds the URL, shown where a configured target shows its property
     */
    public static String proxied(TargetName name, String url, String holder) {
        Proxies provided = proxies;
        if (provided == null || url == null) {
            return url;
        }
        String rewritten = URLS.rewrite(url, new HolderContext(provided, name, holder));
        return rewritten == null ? url : rewritten;
    }

    /**
     * Whether a client was given the target {@code name}, by {@link #proxied} or through its configuration. What a
     * module needs to recognise a built client later, without keeping a record of its own that would outlive the
     * application.
     */
    public static boolean hasTarget(TargetName name) {
        Proxies provided = proxies;
        return provided != null && provided.hasTarget(name);
    }

    private record HolderContext(Proxies proxies, TargetName name, String holder) implements AddressRewriter.Context {

        @Override
        public String property() {
            return holder;
        }

        @Override
        public int proxyPort(Upstream upstream) {
            return proxies.proxyPort(name, upstream, holder);
        }

        // a built client has no configuration around it
        @Override
        public String neighbour(String property) {
            return null;
        }
    }
}
