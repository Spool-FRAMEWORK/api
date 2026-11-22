package software.spool.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.spool.api.configuration.SpoolApiConfiguration;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpoolApiTest {
    @TempDir
    Path folder;

    @Test
    void start_fromItsConfiguration_answersOnItsPort() throws Exception {
        int port = freePort();
        Path file = Files.writeString(folder.resolve("spool-api.yaml"), """
                server: {port: %d}
                authentication:
                  nodes: {type: HMAC_SIGNATURE, configuration: {header: X-Spool-Signature, secret: a-secret, roles: node}}
                cluster:
                  authentication: [nodes]
                  roles: {node: node, read: cluster-reader}
                """.formatted(port));
        SpoolApi api = SpoolApi.start(SpoolApiConfiguration.read(file, Map.<String, String>of()::get), Clock.systemUTC());
        try {
            HttpResponse<String> contract = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/spool/openapi.yaml")).build(),
                    HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> nodes = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/spool/cluster/nodes")).build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(contract.statusCode()).isEqualTo(200);
            assertThat(nodes.statusCode()).isEqualTo(401);
        } finally {
            api.stop();
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
