package io.quarkiverse.resiliencytesting;

/**
 * The files under one directory, from {@link Faults#directory}. A fault on it acts on those files and on no other,
 * whatever disk they share with them.
 */
public interface Directory {

    /**
     * {@code directory:<path>}, the path absolute and without {@code .} and {@code ..}.
     */
    String name();

    /**
     * The disk the directory is on is full, as far as the files under the directory go: every write to one of them
     * fails with the {@code IOException} of a full disk, {@code No space left on device}, whether through
     * {@code java.io} or {@code java.nio}, {@code Files.copy} included. As on a full disk, files can still be
     * created, opened, read and deleted.
     * <p>
     * Unlike a real full disk, the fault stops at the directory: a write to a file anywhere else succeeds, even on
     * the same disk, so the logs of the application and the files of the test are safe.
     * <p>
     * A path is compared as the application gave it when it opened the file, made absolute; symbolic links are not
     * followed. Writes made by native code, an embedded database for one, and through a memory-mapped file are not
     * failed.
     * <p>
     * The test fails if nothing wrote to a file under the directory while the fault was active.
     *
     * @throws IllegalStateException if the fault is already active on this directory
     */
    ActiveFault diskFull();
}
