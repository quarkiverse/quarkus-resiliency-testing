package io.quarkiverse.resiliencytesting.test;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.is;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;

/**
 * A REST Client the application supplies itself, across a dev mode restart. The modules of this extension outlive the
 * application:
 * dev mode keeps their classes and starts the application's own in a new class loader. Nothing of the first
 * application may be left behind: the restarted one has one proxy registry and one target for the client, which a
 * fault on that target reaches.
 */
public class ApplicationSuppliedRestClientDevModeTest {

    @RegisterExtension
    static final QuarkusDevModeTest test = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(PingClient.class, ApplicationSuppliedClientRoutes.class)
                    .addAsResource(new StringAsset("""
                            quarkus.resiliency-testing.enabled=true
                            """), "application.properties"));

    private static void theClientIsProxied(String answer) {
        when()
                .get("/supplied/call")
                .then()
                .statusCode(200)
                .body(is(answer));
        when()
                .get("/supplied/targets")
                .then()
                .body(is("rest-client:PingClient"));
        when()
                .get("/supplied/down")
                .then()
                .body(is("failed"));
        when()
                .get("/supplied/registries")
                .then()
                .body(is("1"));
    }

    @Test
    public void nothingIsLeftOfTheApplicationBeforeTheRestart() {
        theClientIsProxied("pong");

        test.modifySourceFile(ApplicationSuppliedClientRoutes.class, source -> source.replace("\"pong\"", "\"pang\""));

        theClientIsProxied("pang");
    }
}
