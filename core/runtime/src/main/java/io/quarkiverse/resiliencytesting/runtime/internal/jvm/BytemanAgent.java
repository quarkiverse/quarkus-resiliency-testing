package io.quarkiverse.resiliencytesting.runtime.internal.jvm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.jboss.byteman.agent.submit.ScriptText;
import org.jboss.byteman.agent.submit.Submit;

import io.quarkiverse.resiliencytesting.spi.AddressUtil;
import net.bytebuddy.agent.ByteBuddyAgent;

/**
 * The Byteman agent of this JVM, which rewrites the methods a {@link RuleFault} names so that they fail. It is loaded
 * into the running JVM the first time a fault needs it, and stays for the life of the JVM; with no rule installed it
 * changes no class.
 * <p>
 * The agent jar is a resource of this module, not a dependency of the application: Byteman's classes have to be seen
 * by the classes of the JDK it rewrites, so the agent puts its jar on the boot class path, and a second copy on the
 * application's class path would be loaded by the Quarkus class loader as different classes with no rules in them.
 * For the same reason nothing here touches the agent's classes: rules go to it over its local socket, through the
 * small client Byteman ships apart from the agent.
 */
final class BytemanAgent implements RuleAgent {

    /**
     * Where the agent listens, set once it is loaded. A system property because the JVM outlives the application:
     * Quarkus starts each test profile's application in a new class loader, which would find a static field of this
     * class empty and load the agent a second time, which Byteman refuses.
     */
    private static final String PORT_PROPERTY = "quarkus.resiliency-testing.agent.port";

    private static final String AGENT_JAR = "META-INF/quarkus-resiliency-testing/byteman.jar";

    static final BytemanAgent OF_THIS_JVM = new BytemanAgent();

    private BytemanAgent() {
    }

    /**
     * Installs the rules of {@code script}, loading the agent first if this JVM has none yet.
     */
    @Override
    public void install(ScriptText script) {
        try {
            client().addScripts(List.of(script));
        } catch (Exception e) {
            throw new IllegalStateException("The fault could not be applied: " + script.getFileName(), e);
        }
    }

    @Override
    public void uninstall(ScriptText script) {
        try {
            client().deleteScripts(List.of(script));
        } catch (Exception e) {
            throw new IllegalStateException("The fault could not be ended: " + script.getFileName(), e);
        }
    }

    @Override
    public String report() {
        try {
            return client().listAllRules();
        } catch (Exception e) {
            throw new IllegalStateException("The state of the faults could not be read", e);
        }
    }

    private static synchronized Submit client() {
        Integer port = Integer.getInteger(PORT_PROPERTY);
        if (port == null) {
            port = load();
            System.setProperty(PORT_PROPERTY, port.toString());
        }
        return new Submit(AddressUtil.PROXY_HOST, port);
    }

    /**
     * Loads the agent into this JVM and returns the port it listens on. Byte Buddy does the attaching: a JVM does not
     * let itself be attached to, and Byte Buddy goes through a helper process when it has to, as it does for Mockito.
     */
    private static int load() {
        Path jar = extractAgentJar();
        int port = AddressUtil.reservePort();
        // boot: the classes of the JDK are among those rewritten, and they only see the boot class path
        String options = "listener:true,address:" + AddressUtil.PROXY_HOST + ",port:" + port + ",boot:" + jar
                + ",boot:" + injectedFramesJar();
        try {
            ByteBuddyAgent.attach(jar.toFile(), String.valueOf(ProcessHandle.current().pid()), options);
        } catch (RuntimeException e) {
            throw new IllegalStateException("This fault rewrites classes of the running JVM, and the agent that does"
                    + " it could not be loaded. It needs a JDK, not a JRE, and a JVM that allows agents to be loaded"
                    + " at run time: `-XX:+EnableDynamicAgentLoading`. If you are seeing this issue while running tests"
                    + " please report it.", e);
        }
        return port;
    }

    /**
     * A jar holding {@link InjectedFrames} and nothing else, for the boot class path: the rules call it from the
     * classes of the JDK, which do not see the application's class path.
     */
    private static Path injectedFramesJar() {
        String entry = InjectedFrames.class.getName().replace('.', '/') + ".class";
        try (InputStream in = InjectedFrames.class.getClassLoader().getResourceAsStream(entry)) {
            if (in == null) {
                throw new IllegalStateException(entry + " is missing from quarkus-resiliency-testing");
            }
            Path jar = Files.createTempFile("quarkus-resiliency-testing-rules", ".jar");
            jar.toFile().deleteOnExit();
            try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
                out.putNextEntry(new JarEntry(entry));
                in.transferTo(out);
                out.closeEntry();
            }
            return jar;
        } catch (IOException e) {
            throw new IllegalStateException(entry + " could not be copied out of quarkus-resiliency-testing", e);
        }
    }

    private static Path extractAgentJar() {
        try (InputStream in = BytemanAgent.class.getClassLoader().getResourceAsStream(AGENT_JAR)) {
            if (in == null) {
                throw new IllegalStateException(AGENT_JAR + " is missing from quarkus-resiliency-testing");
            }
            Path jar = Files.createTempFile("quarkus-resiliency-testing-agent", ".jar");
            jar.toFile().deleteOnExit();
            Files.copy(in, jar, StandardCopyOption.REPLACE_EXISTING);
            return jar;
        } catch (IOException e) {
            throw new IllegalStateException("The agent jar could not be copied out of quarkus-resiliency-testing", e);
        }
    }
}
