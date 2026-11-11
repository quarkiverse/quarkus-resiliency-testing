package io.quarkiverse.resiliencytesting.runtime.internal.jvm;

import java.util.Arrays;

/**
 * Makes the stack trace of an exception a {@link RuleFault} threw read as if the method the rule is in had thrown
 * it: the frames of the agent that ran the rule, which sit on top of that method's, are taken off.
 * <p>
 * The rules call this class from inside the classes they rewrite, those of the JDK among them, so the agent is given
 * a copy of it on the boot class path. It must therefore use nothing but {@code java.base}.
 */
public final class InjectedFrames {

    private static final String AGENT_PACKAGE = "org.jboss.byteman.";

    private InjectedFrames() {
    }

    /**
     * Drops every frame down to the last one of the agent. An exception no rule threw has none, and is left alone.
     */
    @SuppressWarnings("unused")
    public static void hide(Throwable thrown) {
        StackTraceElement[] frames = thrown.getStackTrace();
        int first = 0;
        for (int i = 0; i < frames.length; i++) {
            if (frames[i].getClassName().startsWith(AGENT_PACKAGE)) {
                first = i + 1;
            }
        }
        if (first > 0) {
            thrown.setStackTrace(Arrays.copyOfRange(frames, first, frames.length));
        }
    }
}
