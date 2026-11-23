package software.spool.api.acceptance;

import io.cucumber.java.After;
import software.spool.api.Cluster;
import software.spool.api.deployment.DslDescriptorValidation;
import software.spool.api.node.MovableClock;
import software.spool.api.route.ClusterRoles;
import software.spool.api.route.ClusterRoutes;
import software.spool.api.route.FirstIdentifying;
import software.spool.core.adapter.http.JdkNodeHttpServer;
import software.spool.core.port.http.NodeHttpServer;
import software.spool.infrastructure.adapter.authentication.hmac.HmacSignatureRequestAuthenticator;
import software.spool.infrastructure.adapter.authentication.hmac.HmacSigner;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Spool API on a free port, with a provider for the nodes, one for the readers and one for the deployer
 * {@value #DEPLOYER}, each with its own secret. It keeps the last answer a step asked for.
 */
public class ApiUnderTest {
    static final String SIGNATURE = "X-Spool-Signature";
    static final String TIMESTAMP = "X-Spool-Timestamp";
    static final String NODE_SECRET = "node-secret";
    static final String READER_SECRET = "reader-secret";
    static final String DEPLOYER = "ana";
    static final String DEPLOYER_SECRET = "ana-secret";

    private final MovableClock clock = new MovableClock();
    private final HttpClient client = HttpClient.newHttpClient();
    private NodeHttpServer server;
    private int port;
    private Optional<HttpResponse<String>> last = Optional.empty();

    public void start(Duration heartbeatTimeout) throws IOException {
        port = freePort();
        server = new JdkNodeHttpServer(port);
        FirstIdentifying authenticator = new FirstIdentifying(List.of(provider("nodes", NODE_SECRET, "node"),
                provider("readers", READER_SECRET, "cluster-reader"), provider(DEPLOYER, DEPLOYER_SECRET, "deployer")));
        ClusterRoutes.of(Cluster.inMemory(heartbeatTimeout, new DslDescriptorValidation(), clock), authenticator,
                new ClusterRoles("node", "cluster-reader", "deployer")).forEach(server::register);
        server.start();
    }

    @After
    public void stop() {
        if (server != null) server.stop();
    }

    public void advance(Duration duration) {
        clock.advance(duration);
    }

    /** Sends the request and keeps its answer as the last one. */
    public HttpResponse<String> send(String method, String path, String body, Optional<String> secret) throws Exception {
        HttpResponse<String> response = query(method, path, body, secret);
        last = Optional.of(response);
        return response;
    }

    public HttpResponse<String> lastResponse() {
        return last.orElseThrow(() -> new IllegalStateException("No step has sent a request yet"));
    }

    /** Sends the request without keeping its answer, to check what the API has. */
    public HttpResponse<String> query(String method, String path, String body, Optional<String> secret) throws Exception {
        byte[] bytes = body.getBytes();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, bytes.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(bytes));
        secret.map(value -> new HmacSigner(SIGNATURE, value, Optional.of(TIMESTAMP), clock).headersFor(bytes))
                .orElse(Map.of()).forEach(request::header);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HmacSignatureRequestAuthenticator provider(String name, String secret, String role) {
        return new HmacSignatureRequestAuthenticator(name, SIGNATURE, secret, Optional.of(TIMESTAMP),
                Duration.ofMinutes(5), Set.of(role), clock);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
