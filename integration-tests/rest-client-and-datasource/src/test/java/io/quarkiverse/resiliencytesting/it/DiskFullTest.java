package io.quarkiverse.resiliencytesting.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.quarkiverse.resiliencytesting.ActiveFault;
import io.quarkiverse.resiliencytesting.Faults;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * A disk with no room left: the write the application makes fails as it would on a full disk, the application
 * answers for it, and the next write goes through once there is room again. No profile: nothing is proxied.
 */
@QuarkusTest
@QuarkusTestResource(InventoryServer.class)
public class DiskFullTest {

    @Inject
    Faults faults;

    @ConfigProperty(name = "reports.directory")
    String reports;

    @Test
    public void aReportThatDoesNotFitIsRefusedAndTheNextOneIsWritten() throws IOException {
        try (ActiveFault f = faults.directory(Path.of(reports)).diskFull()) {
            given()
                    .body("lost")
                    .post("/reports")
                    .then()
                    .statusCode(507)
                    .body(is("No space left on device"));
        }

        given()
                .body("kept")
                .post("/reports")
                .then()
                .statusCode(201);

        assertEquals("kept", Files.readString(Path.of(reports, "report.txt")));
    }
}
