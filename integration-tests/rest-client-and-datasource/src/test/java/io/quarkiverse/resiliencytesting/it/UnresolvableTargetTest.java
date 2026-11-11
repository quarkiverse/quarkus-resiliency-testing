package io.quarkiverse.resiliencytesting.it;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.resiliencytesting.ActiveFault;
import io.quarkiverse.resiliencytesting.Faults;
import io.quarkiverse.resiliencytesting.Target;
import io.quarkiverse.resiliencytesting.junit.NetworkFaultsProfile;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * A dependency whose host name stops resolving: the client fails before it connects, with the exception a failed
 * lookup gives it, and connects again once the name resolves. The REST Client connects through Netty, the JDBC
 * driver through a socket of the JDK.
 */
@QuarkusTest
@TestProfile(NetworkFaultsProfile.class)
@QuarkusTestResource(InventoryServer.class)
public class UnresolvableTargetTest {

    @Inject
    Faults faults;

    @Test
    public void aRestClientFailsWithUnknownHostAndRecovers() {
        Target inventory = faults.target(InventoryClient.class);
        when()
                .post("/orders?item=before")
                .then()
                .statusCode(201);

        // the connection the client keeps alive would carry the next request past the fault
        inventory.dropConnections();
        try (ActiveFault f = inventory.unresolvable()) {
            when()
                    .post("/orders?item=during")
                    .then()
                    .statusCode(503);

            when()
                    .get("/orders/last-failure")
                    .then()
                    .statusCode(200)
                    .body(containsString("java.net.UnknownHostException"));
        }

        when()
                .post("/orders?item=after")
                .then()
                .statusCode(201)
                .body(is("created"));
    }

    @Test
    public void aDatasourceFailsToConnectAndRecovers() {
        Target database = faults.target(DataSource.class);
        when()
                .get("/orders/count")
                .then()
                .statusCode(200);

        // nothing idle in the pool, so the next request needs a new connection
        database.dropConnections();
        try (ActiveFault f = database.unresolvable()) {
            when()
                    .get("/orders/count")
                    .then()
                    .statusCode(500);
        }

        when()
                .get("/orders/count")
                .then()
                .statusCode(200);
    }
}
