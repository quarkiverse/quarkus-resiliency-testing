package io.quarkiverse.resiliencytesting.it;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Writes reports to a directory, and says so when the disk has no room for one.
 */
@jakarta.ws.rs.Path("/reports")
public class ReportResource {

    private final Path directory;

    public ReportResource(@ConfigProperty(name = "reports.directory") String directory) {
        this.directory = Path.of(directory);
    }

    @POST
    public Response write(String report) {
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("report.txt"), report);
        } catch (IOException e) {
            // 507 Insufficient Storage
            return Response.status(507).entity(e.getMessage()).build();
        }
        return Response.status(201).build();
    }
}
