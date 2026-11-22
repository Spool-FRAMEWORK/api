package software.spool.api;

import io.opentelemetry.api.GlobalOpenTelemetry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.spool.api.configuration.SpoolApiConfiguration;
import software.spool.infrastructure.adapter.authentication.hmac.HmacSigner;
import software.spool.runtime.OpenTelemetryConfiguration;
import software.spool.runtime.SpoolRuntime;
import software.spool.runtime.cluster.ClusterClient;
import software.spool.runtime.cluster.ClusterMembership;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The local cluster of the README: the Spool API and two nodes, all real, on this machine. Each node is a process of its
 * own in real use; here they share the JVM, so OpenTelemetry is reset before building each.
 */
class LocalClusterTest {
    private static final String NODE_SECRET = "node-secret";
    private static final String READER_SECRET = "reader-secret";

    @TempDir
    Path folder;

    @Test
    void twoNodes_registerAndBeat_andTheApiListsThemUp() throws Exception {
        int apiPort = freePort();
        SpoolApi api = SpoolApi.start(SpoolApiConfiguration.read(configuration(apiPort), Map.<String, String>of()::get),
                Clock.systemUTC());
        List<SpoolRuntime> nodes = List.of(node("node-a", apiPort), node("node-b", apiPort));
        try {
            nodes.forEach(SpoolRuntime::start);

            assertThat(listWithin(apiPort, 5)).contains("\"id\":\"node-a\"", "\"id\":\"node-b\"").doesNotContain("DOWN");
        } finally {
            nodes.forEach(SpoolRuntime::stop);
            api.stop();
        }
    }

    private Path configuration(int port) throws Exception {
        return Files.writeString(folder.resolve("spool-api.yaml"), """
                server: {port: %d}
                authentication:
                  nodes: {type: HMAC_SIGNATURE, configuration: {header: %s, timestampHeader: %s, secret: %s, roles: node}}
                  readers: {type: HMAC_SIGNATURE, configuration: {header: %s, timestampHeader: %s, secret: %s, roles: cluster-reader}}
                cluster:
                  authentication: [nodes, readers]
                  heartbeatTimeout: 5s
                  roles: {node: node, read: cluster-reader}
                """.formatted(port, ClusterClient.SIGNATURE, ClusterClient.TIMESTAMP, NODE_SECRET,
                ClusterClient.SIGNATURE, ClusterClient.TIMESTAMP, READER_SECRET));
    }

    private static SpoolRuntime node(String id, int apiPort) throws Exception {
        Map<String, String> environment = Map.of(
                ClusterMembership.API, "http://localhost:" + apiPort,
                ClusterMembership.NODE_ID, id,
                ClusterMembership.NODE_ADDRESS, "http://localhost:" + freePort(),
                ClusterMembership.SECRET, NODE_SECRET,
                ClusterMembership.HEARTBEAT, "1s");
        GlobalOpenTelemetry.resetForTest();
        return SpoolRuntime.builder()
                .OpenTelemetryConfiguration(OpenTelemetryConfiguration.builder().serviceName("spool-" + id).build())
                .withEnvironment(environment::get)
                .build();
    }

    private static String listWithin(int apiPort, int seconds) throws Exception {
        Instant deadline = Instant.now().plusSeconds(seconds);
        String body = "";
        while (Instant.now().isBefore(deadline)) {
            body = list(apiPort);
            if (body.contains("node-a") && body.contains("node-b")) return body;
            Thread.sleep(200);
        }
        return body;
    }

    private static String list(int apiPort) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + apiPort + "/spool/cluster/nodes"));
        new HmacSigner(ClusterClient.SIGNATURE, READER_SECRET, Optional.of(ClusterClient.TIMESTAMP), Clock.systemUTC())
                .headersFor(new byte[0]).forEach(request::header);
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
