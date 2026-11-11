package io.quarkiverse.resiliencytesting.runtime.internal.jvm;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jboss.byteman.agent.submit.ScriptText;

import io.quarkiverse.resiliencytesting.spi.AddressUtil;

/**
 * A fault that no proxy can produce, because what fails is not a connection: a write to a full disk, a host name
 * that does not resolve. It is made of rules, each of which names a method of the JDK or of Netty and has it throw
 * what it would throw if the failure were real, and is active from {@link #apply()} to {@link #remove()}.
 * <p>
 * The methods named are the public ones, or the ones every public path goes through, of JDK 21 and later; a rule
 * whose method a JDK does not have is reported by {@link #remove()} rather than silently doing nothing.
 */
public final class RuleFault {

    private final String subject;
    private final String description;
    private final String untriggered;
    // names the rules of this fault, so that they can be told from those of another in the agent's report
    private final String id = UUID.randomUUID().toString();
    // set by the rules when one of them fires: the rules run in the classes they rewrite, those of the JDK among
    // them, and a system property is the one place both they and this class can reach
    private final String hitProperty = "quarkus.resiliency-testing.fault." + id + ".hit";
    private final String rules;
    private RuleAgent agent = BytemanAgent.OF_THIS_JVM;
    private ScriptText script;
    private boolean triggered;

    /**
     * @param template the script of the fault, see {@link RuleTemplates}
     * @param data what the template leaves open, besides the name of the fault and of its hit property
     */
    private RuleFault(String subject, String description, String untriggered, String template,
            Map<String, Object> data) {
        this.subject = subject;
        this.description = description;
        this.untriggered = untriggered;
        Map<String, Object> all = new HashMap<>(data);
        all.put("fault", id);
        all.put("hit", hitProperty);
        this.rules = RuleTemplates.render(template, all);
    }

    /**
     * Writes to the files under {@code directory} fail as they do when the disk is full. Files can still be created,
     * opened, read and deleted, as on a full disk. The rules are in {@code disk-full.btm}.
     */
    public static RuleFault diskFull(Path directory) {
        String dir = directory.toAbsolutePath().normalize().toString();
        return new RuleFault("directory:" + dir, "diskFull",
                "Directory " + dir + " had the 'diskFull' fault active, but nothing wrote to a file under it while"
                        + " it was: the code under test writes somewhere else, or not through java.io or java.nio,"
                        + " so the fault was not tested",
                "disk-full", Map.of("directory", literal(dir)));
    }

    /**
     * Looking up {@code host} fails as it does when no name server knows it, in the resolver of the JDK and in the
     * one of Netty, which Vert.x uses. The rules are in {@code unresolvable-host.btm}.
     */
    public static RuleFault unresolvableHost(String host) {
        String name = host.toLowerCase(Locale.ROOT);
        return new RuleFault("host:" + name, "unresolvable",
                "Host " + name + " was unresolvable, but nothing looked it up while it was: the code under test"
                        + " connects to another host, to an IP address, or over a connection it already had, so the"
                        + " fault was not tested",
                "unresolvable-host", Map.of("host", literal(name)));
    }

    /**
     * Connecting to the proxies of a target fails as connecting to the host behind each does when its name does not
     * resolve. The application reaches a proxied target by the address of its proxy, so it looks no name up; the
     * failure is raised where its lookup would have failed, before a connection is made. The rules are in
     * {@code unresolvable-target.btm}.
     *
     * @param target the name of the target the proxies stand for
     * @param hostsByProxyPort the port of each proxy, and the host name the application was configured with for it
     */
    public static RuleFault unresolvableTarget(String target, Map<Integer, String> hostsByProxyPort) {
        List<Proxy> proxies = new ArrayList<>();
        for (Map.Entry<Integer, String> proxy : hostsByProxyPort.entrySet()) {
            proxies.add(new Proxy(proxy.getKey(), literal(proxy.getValue())));
        }
        return new RuleFault(target, "unresolvable",
                "Target " + target + " was unresolvable, but nothing connected to it while it was: the request under"
                        + " test does not use this target, or it went over a connection opened before the fault,"
                        + " which dropConnections() would have closed, so the fault was not tested",
                "unresolvable-target", Map.of("proxyHost", AddressUtil.PROXY_HOST, "proxies", proxies));
    }

    /**
     * One proxy of a target, as {@code unresolvable-target.btm} reads it.
     *
     * @param port where the proxy listens
     * @param host the host name the application was configured with, the one that does not resolve
     */
    public record Proxy(int port, String host) {
    }

    /**
     * {@code text} as it goes between the quotes of a string literal of the rule language, which escapes as Java
     * does: a template puts what it is given into such literals.
     */
    private static String literal(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * What the fault is applied to, as the faults on targets name theirs: a target, {@code directory:<path>},
     * {@code host:<name>}.
     */
    public String subject() {
        return subject;
    }

    /**
     * What the fault does to its subject, as the method that applies it is called.
     */
    public String description() {
        return description;
    }

    /**
     * This fault, applied and removed through {@code agent} instead of the agent of this JVM.
     */
    RuleFault through(RuleAgent agent) {
        this.agent = agent;
        return this;
    }

    /**
     * Puts the rules in place: from here on the methods they name fail.
     */
    public void apply() {
        script = new ScriptText(subject + " " + description, rules);
        agent.install(script);
    }

    /**
     * Takes the rules out, so that the methods they name work again. They are taken out whatever else fails: the
     * report is read first, because it lists a rule only while it is installed, but a report that cannot be read
     * does not keep the rules in.
     *
     * @throws IllegalStateException if a rule could not be injected into the method it names, which means the fault
     *         did less than it says on this JDK, or if the agent could not be asked
     */
    public void remove() {
        RuntimeException failure = null;
        Set<String> errors = Set.of();
        try {
            errors = errors(agent.report());
        } catch (RuntimeException e) {
            failure = e;
        }
        try {
            agent.uninstall(script);
        } catch (RuntimeException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        triggered = System.clearProperty(hitProperty) != null;
        if (failure != null) {
            throw failure;
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("The '" + description + "' fault on " + subject + " could not be applied"
                    + " in full on Java " + Runtime.version() + ":\n  " + String.join("\n  ", errors));
        }
    }

    /**
     * The errors the agent reported for the rules of this fault; empty when it reported none. The agent lists every
     * rule: its text, from {@code RULE} to {@code ENDRULE}, then the methods it was injected into, or the exception
     * that kept it out of one, followed by the lines of its message.
     */
    private Set<String> errors(String report) {
        Set<String> errors = new LinkedHashSet<>();
        boolean ours = false;
        boolean text = false;
        boolean failed = false;
        for (String line : report.split("\n")) {
            if (line.startsWith("RULE ")) {
                ours = line.contains(id);
                text = true;
                failed = false;
            } else if (line.equals("ENDRULE")) {
                text = false;
            } else if (ours && !text) {
                if (line.contains("Exception")) {
                    failed = true;
                }
                // the agent names the exception twice, the first time after "threw"
                if (failed && !line.startsWith("threw ") && !line.startsWith("# File ")) {
                    errors.add(line.trim());
                }
            }
        }
        return errors;
    }

    /**
     * Whether any rule fired while the fault was active: whether something it acts on happened meanwhile. Known once
     * {@link #remove()} has returned.
     */
    public boolean triggered() {
        return triggered;
    }

    /**
     * What to tell the test when the fault ended without having fired.
     */
    public String untriggered() {
        return untriggered;
    }
}
