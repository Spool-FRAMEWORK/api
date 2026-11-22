package software.spool.api.acceptance;

import io.cucumber.java.After;
import software.spool.api.node.HeartbeatTimeout;
import software.spool.api.node.InMemoryClusterNodes;
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

/** The Spool API on a free port, with a node provider and a reader provider, each with its own secret. */
public class ApiUnderTest {
    static final String SIGNATURE = "X-Spool-Signature";
    static final String TIMESTAMP = "X-Spool-Timestamp";
    static final String NODE_SECRET = "node-secret";
    static final String READER_SECRET = "reader-secret";

    private final MovableClock clock = new MovableClock();
    private final HttpClient client = HttpClient.newHttpClient();
    private NodeHttpServer server;
    private int port;

    public void start(Duration heartbeatTimeout) throws IOException {
        port = freePort();
        server = new JdkNodeHttpServer(port);
        FirstIdentifying authenticator = new FirstIdentifying(List.of(provider("nodes", NODE_SECRET, "node"),
                provider("readers", READER_SECRET, "cluster-reader")));
        ClusterRoutes.of(new InMemoryClusterNodes(clock), new HeartbeatTimeout(heartbeatTimeout, clock), authenticator,
                new ClusterRoles("node", "cluster-reader")).forEach(server::register);
        server.start();
    }

    @After
    public void stop() {
        if (server != null) server.stop();
    }

    public void advance(Duration duration) {
        clock.advance(duration);
    }

    public HttpResponse<String> send(String method, String path, String body, Optional<String> secret) throws Exception {
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
