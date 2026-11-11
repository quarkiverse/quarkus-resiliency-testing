package io.quarkiverse.resiliencytesting.runtime.internal.jvm;

import org.jboss.byteman.agent.submit.ScriptText;

/**
 * What a {@link RuleFault} needs of the agent that rewrites the classes: {@link BytemanAgent}, and in tests one that
 * fails where the real one would have to be broken to.
 */
interface RuleAgent {

    /**
     * Installs the rules of {@code script}.
     */
    void install(ScriptText script);

    /**
     * Removes the rules of {@code script}, which gives the methods they were injected into their own code back.
     */
    void uninstall(ScriptText script);

    /**
     * What the agent reports of every rule installed: the methods it was injected into, and the error if it could
     * not be.
     */
    String report();
}
