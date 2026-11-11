package io.quarkiverse.resiliencytesting.runtime.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.resiliencytesting.ActiveFault;
import io.quarkiverse.resiliencytesting.Directory;
import io.quarkiverse.resiliencytesting.Host;
import io.quarkiverse.resiliencytesting.spi.ConsequenceCleaner;

/**
 * The faults that act inside the JVM, as a test gets them from {@code Faults}: they need no profile, they are
 * cleaned up after and judged by the guard like the faults on targets, and one subject takes one at a time.
 */
class InjectedFaultsTest {

    private final AtomicInteger cleanups = new AtomicInteger();
    private final ConsequenceCleaner cleaner = cleanups::incrementAndGet;
    // fault injection off: no profile, nothing proxied
    private final FaultsImpl faults = new FaultsImpl(false, List.of(), List.of(cleaner), List.of(), List.of());

    @TempDir
    Path directory;

    @AfterEach
    void endFaults() {
        faults.resetAll();
        faults.takeGuardViolations();
    }

    @Test
    void aDirectoryWhoseDiskIsFullThatMetAWritePassesTheGuardAndIsCleanedUpAfter() throws IOException {
        Path file = directory.resolve("file.txt");
        Directory reports = faults.directory(directory);
        assertEquals("directory:" + directory.toAbsolutePath().normalize(), reports.name());

        try (ActiveFault f = reports.diskFull()) {
            assertEquals(1, faults.activeFaultCount());
            assertThrows(IOException.class, () -> Files.writeString(file, "lost"));
        }

        assertEquals(0, faults.activeFaultCount());
        assertEquals(1, cleanups.get());
        assertEquals(List.of(), faults.takeGuardViolations());
        Files.writeString(file, "kept");
    }

    @Test
    void aDirectoryWhoseDiskIsFullThatNothingWroteToFailsTheGuard() {
        faults.directory(directory).diskFull().end();

        List<String> violations = faults.takeGuardViolations();
        assertEquals(1, violations.size());
        assertTrue(violations.getFirst().contains("nothing wrote to a file under it"), violations.getFirst());
    }

    @Test
    void anUnresolvableHostNothingLookedUpFailsTheGuard() {
        Host host = faults.host("Inventory.Example.com");
        assertEquals("host:inventory.example.com", host.name());

        host.unresolvable().end();

        List<String> violations = faults.takeGuardViolations();
        assertEquals(1, violations.size());
        assertTrue(violations.getFirst().contains("nothing looked it up"), violations.getFirst());
    }

    @Test
    void anUnresolvableHostThatWasLookedUpPassesTheGuard() {
        try (ActiveFault f = faults.host("inventory.example.com").unresolvable()) {
            assertThrows(UnknownHostException.class, () -> InetAddress.getByName("inventory.example.com"));
        }

        assertEquals(List.of(), faults.takeGuardViolations());
    }

    @Test
    void anAddressIsNotAHost() {
        assertThrows(IllegalArgumentException.class, () -> faults.host("127.0.0.1"));
        assertThrows(IllegalArgumentException.class, () -> faults.host("::1"));
    }

    @Test
    void aSubjectTakesOneFaultAtATime() {
        try (ActiveFault f = faults.directory(directory).diskFull()) {
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> faults.directory(directory).diskFull());
            assertTrue(e.getMessage().contains("already has the 'diskFull' fault active"), e.getMessage());
        }
    }

    @Test
    void resetAllEndsThem() throws IOException {
        faults.directory(directory).diskFull();

        faults.resetAll();

        assertEquals(0, faults.activeFaultCount());
        Files.writeString(directory.resolve("file.txt"), "kept");
    }

    /**
     * How many times two threads race to fill the disk of the same directory. Applying a fault is a round trip to the agent,
     * long
     * enough for both to get past a check made before it; a handful of rounds is there for the one where they do not
     * overlap.
     */
    private static final int RACES = 5;

    @Test
    void ofTwoThreadsFillingTheDiskOfTheSameDirectoryAtOnceOneIsRefusedAndNothingStaysBehind() throws Exception {
        Path file = directory.resolve("file.txt");
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            for (int race = 0; race < RACES; race++) {
                CyclicBarrier together = new CyclicBarrier(2);
                Callable<ActiveFault> fill = new Callable<>() {
                    @Override
                    public ActiveFault call() throws Exception {
                        together.await();
                        try {
                            return faults.directory(directory).diskFull();
                        } catch (IllegalStateException alreadyFull) {
                            return null;
                        }
                    }
                };
                Future<ActiveFault> first = threads.submit(fill);
                Future<ActiveFault> second = threads.submit(fill);
                List<ActiveFault> applied = new ArrayList<>();
                if (first.get() != null) {
                    applied.add(first.get());
                }
                if (second.get() != null) {
                    applied.add(second.get());
                }

                assertEquals(1, applied.size());
                assertThrows(IOException.class, () -> Files.writeString(file, "lost"));
                applied.getFirst().end();
                Files.writeString(file, "kept");
            }
        } finally {
            threads.shutdownNow();
        }
    }
}
