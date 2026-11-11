package io.quarkiverse.resiliencytesting.runtime.internal.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import org.jboss.byteman.agent.submit.ScriptText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

/**
 * The rules against the JDK the tests run on: every way of writing a file and of looking a name up that a fault
 * claims to fail does fail, only where the fault says, and works again once the fault is over.
 */
class RuleFaultTest {

    private static final byte[] DATA = "data".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path full;

    @TempDir
    Path elsewhere;

    @Test
    void aFullDiskFailsEveryWriteUnderTheDirectoryAndNoOther() throws IOException {
        Path file = full.resolve("sub/file.txt");
        Files.createDirectories(file.getParent());
        Path source = Files.write(elsewhere.resolve("source.txt"), DATA);

        RuleFault fault = RuleFault.diskFull(full);
        fault.apply();
        try {
            assertNoSpace(() -> {
                try (FileOutputStream out = new FileOutputStream(file.toFile())) {
                    out.write(DATA);
                }
            });
            assertNoSpace(() -> {
                try (FileOutputStream out = new FileOutputStream(file.toFile())) {
                    out.write('d');
                }
            });
            assertNoSpace(() -> {
                try (FileWriter out = new FileWriter(file.toFile())) {
                    out.write("data");
                }
            });
            assertNoSpace(() -> {
                try (RandomAccessFile out = new RandomAccessFile(file.toFile(), "rw")) {
                    out.write(DATA);
                }
            });
            assertNoSpace(() -> {
                try (RandomAccessFile out = new RandomAccessFile(file.toFile(), "rw")) {
                    out.writeBytes("data");
                }
            });
            assertNoSpace(() -> Files.write(file, DATA));
            assertNoSpace(() -> Files.writeString(file, "data"));
            assertNoSpace(() -> {
                try (FileChannel out = FileChannel.open(file, StandardOpenOption.WRITE)) {
                    out.write(ByteBuffer.wrap(DATA), 0);
                }
            });
            assertNoSpace(() -> {
                try (FileChannel in = FileChannel.open(source);
                        FileChannel out = FileChannel.open(file,
                                StandardOpenOption.WRITE)) {
                    out.transferFrom(in, 0, DATA.length);
                }
            });
            FileSystemException copy = assertThrows(FileSystemException.class,
                    () -> Files.copy(source, full.resolve("copy.txt")));
            assertEquals("No space left on device", copy.getReason());
            assertThrownByTheJdk(copy);

            // a full disk still lets files be created, read and deleted
            Files.createFile(full.resolve("empty.txt"));
            assertEquals(0, Files.size(full.resolve("empty.txt")));
            Files.delete(full.resolve("empty.txt"));

            // and another directory is another disk
            Files.write(elsewhere.resolve("other.txt"), DATA);
        } finally {
            fault.remove();
        }
        assertTrue(fault.triggered());

        Files.write(file, DATA);
        assertEquals("data", Files.readString(file));
    }

    @Test
    void aFullDiskNothingWroteToWasNotTriggered() throws IOException {
        RuleFault fault = RuleFault.diskFull(full);
        fault.apply();
        try {
            Files.write(elsewhere.resolve("other.txt"), DATA);
        } finally {
            fault.remove();
        }
        assertFalse(fault.triggered());
    }

    @Test
    void anUnresolvableHostFailsTheLookupsOfTheJdkAndNoOther() throws IOException {
        RuleFault fault = RuleFault.unresolvableHost("LocalHost");
        fault.apply();
        try {
            UnknownHostException e = assertThrows(UnknownHostException.class, () -> InetAddress.getByName("localhost"));
            assertEquals("localhost: Name or service not known", e.getMessage());
            assertThrownByTheJdk(e);
            assertThrows(UnknownHostException.class, () -> InetAddress.getAllByName("LOCALHOST"));
            assertTrue(new InetSocketAddress("localhost", 80).isUnresolved());
            assertThrows(UnknownHostException.class, () -> new Socket("localhost", 80));

            // an address is not a name
            assertTrue(InetAddress.getByName("127.0.0.1").isLoopbackAddress());
        } finally {
            fault.remove();
        }
        assertTrue(fault.triggered());

        assertTrue(InetAddress.getByName("localhost").isLoopbackAddress());
    }

    @Test
    void anUnresolvableTargetFailsConnectsToItsProxyAndNoOther() throws IOException {
        try (ServerSocket proxy = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"));
                ServerSocket other = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
            InetSocketAddress toProxy = new InetSocketAddress("127.0.0.1", proxy.getLocalPort());
            InetSocketAddress toOther = new InetSocketAddress("127.0.0.1", other.getLocalPort());

            RuleFault fault = RuleFault.unresolvableTarget("rest-client:inventory",
                    Map.of(proxy.getLocalPort(), "inventory.example.com"));
            fault.apply();
            try {
                UnknownHostException e = assertThrows(UnknownHostException.class, () -> {
                    try (Socket socket = new Socket()) {
                        socket.connect(toProxy);
                    }
                });
                assertEquals("inventory.example.com: Name or service not known", e.getMessage());
                assertThrownByTheJdk(e);
                assertThrownByTheJdk(assertThrows(UnknownHostException.class, () -> {
                    try (SocketChannel channel = SocketChannel.open()) {
                        channel.connect(toProxy);
                    }
                }));

                try (Socket socket = new Socket()) {
                    socket.connect(toOther);
                }
                try (SocketChannel channel = SocketChannel.open()) {
                    channel.connect(toOther);
                }
            } finally {
                fault.remove();
            }
            assertTrue(fault.triggered());

            try (Socket socket = new Socket()) {
                socket.connect(toProxy);
            }
        }
    }

    @Test
    void aFaultWhoseStateCannotBeReadIsStillRemoved() throws IOException {
        IllegalStateException unreadable = new IllegalStateException("the report could not be read");
        RuleAgent agent = new RuleAgent() {
            @Override
            public void install(ScriptText script) {
                BytemanAgent.OF_THIS_JVM.install(script);
            }

            @Override
            public void uninstall(ScriptText script) {
                BytemanAgent.OF_THIS_JVM.uninstall(script);
            }

            @Override
            public String report() {
                throw unreadable;
            }
        };
        Path file = full.resolve("file.txt");
        RuleFault fault = RuleFault.diskFull(full).through(agent);
        fault.apply();
        assertNoSpace(() -> Files.write(file, DATA));

        assertEquals(unreadable, assertThrows(IllegalStateException.class, fault::remove));

        assertTrue(fault.triggered());
        Files.write(file, DATA);
    }

    private static void assertNoSpace(Executable write) {
        IOException e = assertThrows(IOException.class, write);
        assertEquals("No space left on device", e.getMessage());
        assertThrownByTheJdk(e);
    }

    /**
     * The stack trace starts in the JDK, where the real failure would be thrown, and shows nothing of what threw it.
     */
    private static void assertThrownByTheJdk(Throwable thrown) {
        StackTraceElement[] frames = thrown.getStackTrace();
        assertTrue(frames[0].getClassName().startsWith("java.") || frames[0].getClassName().startsWith("sun."),
                frames[0].toString());
        for (StackTraceElement frame : frames) {
            assertFalse(frame.getClassName().startsWith("org.jboss.byteman."), frame.toString());
        }
    }
}
