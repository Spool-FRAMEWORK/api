package software.spool.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.GlobalOpenTelemetry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.spool.api.configuration.SpoolApiConfiguration;
import software.spool.infrastructure.adapter.authentication.hmac.HmacSigner;
import software.spool.runtime.OpenTelemetryConfiguration;
import software.spool.runtime.SpoolRuntime;
import software.spool.runtime.cluster.ClusterClient;
import software.spool.runtime.cluster.ClusterMembership;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The local cluster of the README: the Spool API and two nodes, all real, on this machine. Each node is a process of its
 * own in real use; here they share the JVM, so OpenTelemetry is reset before building each.
 */
class LocalClusterTest {
    private static final String NODE_SECRET = "node-secret";
    private static final String READER_SECRET = "reader-secret";
    private static final String DEPLOYER_SECRET = "deployer-secret";
    private static final String MODULE = "\"orders-ingester\"";
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path folder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void twoNodes_registerAndBeat_andTheApiListsThemUp() throws Exception {
        int apiPort = freePort();
        SpoolApi api = startApi(apiPort, 5);
        List<SpoolRuntime> nodes = List.of(node("node-a", apiPort, freePort()), node("node-b", apiPort, freePort()));
        try {
            nodes.forEach(SpoolRuntime::start);

            assertThat(within(10, () -> list(apiPort, "/spool/cluster/nodes"),
                    body -> body.contains("\"id\":\"node-a\"") && body.contains("\"id\":\"node-b\"")))
                    .contains("\"id\":\"node-a\"", "\"id\":\"node-b\"").doesNotContain("DOWN");
        } finally {
            nodes.forEach(SpoolRuntime::stop);
            api.stop();
        }
    }

    @Test
    void aDeployment_runsOnANode_movesToTheOtherWhenItsNodeStops_andStopsWhenWithdrawn() throws Exception {
        int apiPort = freePort();
        SpoolApi api = startApi(apiPort, 3);
        Map<String, Integer> ports = Map.of("node-a", freePort(), "node-b", freePort());
        Map<String, SpoolRuntime> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> port : ports.entrySet()) nodes.put(port.getKey(), node(port.getKey(), apiPort, port.getValue()));
        try {
            nodes.values().forEach(SpoolRuntime::start);
            within(10, () -> list(apiPort, "/spool/cluster/nodes"), body -> body.contains("node-a") && body.contains("node-b"));

            assertThat(deploy(apiPort, "orders").statusCode()).isEqualTo(201);
            String first = runningDeployment(apiPort).get("node").asText();
            assertThat(within(30, () -> health(ports.get(first)), body -> body.contains(MODULE))).contains(MODULE);

            nodes.remove(first).stop();
            String second = first.equals("node-a") ? "node-b" : "node-a";
            assertThat(within(30, () -> list(apiPort, "/spool/deployments"),
                    body -> body.contains("\"node\":\"" + second + "\"") && body.contains("\"state\":\"RUNNING\"")))
                    .contains("\"node\":\"" + second + "\"");
            assertThat(within(30, () -> health(ports.get(second)), body -> body.contains(MODULE))).contains(MODULE);

            assertThat(send(apiPort, "DELETE", "/spool/deployments/orders", "", DEPLOYER_SECRET).statusCode()).isEqualTo(204);
            assertThat(within(10, () -> health(ports.get(second)), body -> !body.contains(MODULE))).doesNotContain(MODULE);
        } finally {
            nodes.values().forEach(SpoolRuntime::stop);
            api.stop();
        }
    }

    private SpoolApi startApi(int port, int heartbeatTimeoutSeconds) throws Exception {
        return SpoolApi.start(SpoolApiConfiguration.read(configuration(port, heartbeatTimeoutSeconds),
                Map.<String, String>of()::get), Clock.systemUTC());
    }

    private Path configuration(int port, int heartbeatTimeoutSeconds) throws IOException {
        return Files.writeString(folder.resolve("spool-api.yaml"), """
                server: {port: %d}
                authentication:
                  nodes: {type: HMAC_SIGNATURE, configuration: {header: %s, timestampHeader: %s, secret: %s, roles: node}}
                  readers: {type: HMAC_SIGNATURE, configuration: {header: %s, timestampHeader: %s, secret: %s, roles: cluster-reader}}
                  deployers: {type: HMAC_SIGNATURE, configuration: {header: %s, timestampHeader: %s, secret: %s, roles: deployer}}
                cluster:
                  authentication: [nodes, readers, deployers]
                  heartbeatTimeout: %ds
                  roles: {node: node, read: cluster-reader, deploy: deployer}
                """.formatted(port, ClusterClient.SIGNATURE, ClusterClient.TIMESTAMP, NODE_SECRET,
                ClusterClient.SIGNATURE, ClusterClient.TIMESTAMP, READER_SECRET,
                ClusterClient.SIGNATURE, ClusterClient.TIMESTAMP, DEPLOYER_SECRET, heartbeatTimeoutSeconds));
    }

    private static SpoolRuntime node(String id, int apiPort, int port) {
        Map<String, String> environment = Map.of(
                ClusterMembership.API, "http://localhost:" + apiPort,
                ClusterMembership.NODE_ID, id,
                ClusterMembership.NODE_ADDRESS, "http://localhost:" + port,
                ClusterMembership.SECRET, NODE_SECRET,
                ClusterMembership.HEARTBEAT, "1s");
        GlobalOpenTelemetry.resetForTest();
        return SpoolRuntime.builder()
                .OpenTelemetryConfiguration(OpenTelemetryConfiguration.builder().serviceName("spool-" + id).build())
                .withEnvironment(environment::get)
                .build();
    }

    private HttpResponse<String> deploy(int apiPort, String name) throws Exception {
        Path data = Files.createDirectories(folder.resolve(name));
        String descriptor = """
                infrastructure:
                  bus: {kind: EVENT_BUS, type: IN_MEMORY}
                  inbox: {kind: INBOX, type: FILE_SYSTEM, configuration: {path: "%s"}}
                  lake: {kind: DATA_LAKE, type: FILE_SYSTEM, configuration: {path: "%s"}}
                modules:
                  - ingester: {type: REACTIVE, id: %s-ingester}
                """.formatted(slashed(data.resolve("inbox")), slashed(data.resolve("lake")), name);
        return send(apiPort, "PUT", "/spool/deployments/" + name, descriptor, DEPLOYER_SECRET);
    }

    private JsonNode runningDeployment(int apiPort) throws Exception {
        String body = within(30, () -> list(apiPort, "/spool/deployments"), json -> json.contains("\"state\":\"RUNNING\""));
        assertThat(body).contains("\"state\":\"RUNNING\"");
        return JSON.readTree(body).get("deployments").get(0);
    }

    private String list(int apiPort, String path) throws Exception {
        return send(apiPort, "GET", path, "", READER_SECRET).body();
    }

    private String health(int port) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/spool/health")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    private HttpResponse<String> send(int apiPort, String method, String path, String body, String secret) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + apiPort + path))
                .method(method, bytes.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(bytes));
        new HmacSigner(ClusterClient.SIGNATURE, secret, Optional.of(ClusterClient.TIMESTAMP), Clock.systemUTC())
                .headersFor(bytes).forEach(request::header);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** What {@code fetch} gives once {@code done} accepts it, or the last thing it gave when the time is up. */
    private static String within(int seconds, Fetch fetch, Predicate<String> done) throws Exception {
        Instant deadline = Instant.now().plusSeconds(seconds);
        String last = "";
        while (Instant.now().isBefore(deadline)) {
            try {
                last = fetch.get();
                if (done.test(last)) return last;
            } catch (IOException notAnsweringYet) {
                last = String.valueOf(notAnsweringYet.getMessage());
            }
            Thread.sleep(200);
        }
        return last;
    }

    @FunctionalInterface
    private interface Fetch {
        String get() throws Exception;
    }

    private static String slashed(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
