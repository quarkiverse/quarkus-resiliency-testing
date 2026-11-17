package io.quarkiverse.resiliencytesting.runtime.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.junit.jupiter.api.Test;

import io.quarkiverse.resiliencytesting.spi.ReflectedField;

/**
 * A private field of another extension's class, read, and what is said when the class has changed.
 */
class ReflectedFieldTest {

    /**
     * Stands in for a class of another extension.
     */
    private static final class Transport {
        private final String url;

        private Transport(String url) {
            this.url = url;
        }
    }

    @Test
    void aPrivateFieldIsRead() {
        ReflectedField<String> url = ReflectedField.of(Transport.class, "url", String.class);

        assertEquals("http://remote/mcp", url.read(new Transport("http://remote/mcp")));
    }

    @Test
    void aFieldThatIsGoneSaysWhichClassChangedAndWhereItCameFrom() {
        ReflectedField<String> endpoint = ReflectedField.of(Transport.class, "endpoint", String.class);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> endpoint.read(new Transport("http://remote/mcp")));

        String message = e.getMessage();
        assertTrue(message.contains("the field 'endpoint' (java.lang.String) of " + Transport.class.getName()), message);
        assertTrue(message.contains("cannot be read"), message);
        assertTrue(message.contains("NoSuchFieldException"), message);
        assertTrue(message.contains("loaded from " + Transport.class.getProtectionDomain().getCodeSource().getLocation()),
                message);
        assertTrue(message.contains("needs updating"), message);
    }

    @Test
    void aFieldOfAnotherTypeIsNotTakenForTheOneDeclared() {
        ReflectedField<URI> url = ReflectedField.of(Transport.class, "url", URI.class);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> url.read(new Transport("http://remote/mcp")));

        assertTrue(e.getMessage().contains("the field 'url' (java.net.URI)"), e.getMessage());
        assertTrue(e.getMessage().contains("NoSuchFieldException"), e.getMessage());
    }
}
