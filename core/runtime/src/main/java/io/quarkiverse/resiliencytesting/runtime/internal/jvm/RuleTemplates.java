package io.quarkiverse.resiliencytesting.runtime.internal.jvm;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import io.quarkus.qute.Engine;
import io.quarkus.qute.ReflectionValueResolver;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.qute.TemplateLocator;
import io.quarkus.qute.Variant;

/**
 * The rules of each {@link RuleFault}, kept as templates next to this module's classes so that a rule can be read as
 * the agent gets it: {@code META-INF/quarkus-resiliency-testing/rules/<fault>.btm}, a Byteman script in which only
 * what a test chooses, a directory, a host name, is left open.
 * <p>
 * The engine is Qute's own, built here: the Qute extension would add itself to the application under test.
 */
final class RuleTemplates {

    private static final String LOCATION = "META-INF/quarkus-resiliency-testing/rules/";
    private static final String SUFFIX = ".btm";

    private static final Engine ENGINE = Engine.builder()
            .addDefaults()
            // for the records a template is given
            .addValueResolver(new ReflectionValueResolver())
            .addLocator(new Resources())
            .build();

    private RuleTemplates() {
    }

    /**
     * The script of the fault {@code name}, with {@code data} filled in.
     */
    static String render(String name, Map<String, Object> data) {
        TemplateInstance template = ENGINE.getTemplate(name).instance();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            template.data(entry.getKey(), entry.getValue());
        }
        return template.render();
    }

    /**
     * Finds a template, and the templates it includes, among the resources of this module.
     */
    private static final class Resources implements TemplateLocator {

        @Override
        public Optional<TemplateLocation> locate(String id) {
            String resource = LOCATION + id + SUFFIX;
            if (RuleTemplates.class.getClassLoader().getResource(resource) == null) {
                return Optional.empty();
            }
            return Optional.of(new Resource(resource));
        }
    }

    /**
     * One template. It is plain text: nothing in it is escaped.
     */
    private record Resource(String resource) implements TemplateLocator.TemplateLocation {

        @Override
        public Reader read() {
            InputStream in = RuleTemplates.class.getClassLoader().getResourceAsStream(resource);
            if (in == null) {
                throw new UncheckedIOException(new IOException(resource + " is missing from quarkus-resiliency-testing"));
            }
            return new InputStreamReader(in, StandardCharsets.UTF_8);
        }

        @Override
        public Optional<Variant> getVariant() {
            return Optional.empty();
        }
    }
}
