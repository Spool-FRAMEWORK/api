package software.spool.api;

import software.spool.api.configuration.SpoolApiConfiguration;
import software.spool.api.node.HeartbeatTimeout;
import software.spool.api.node.InMemoryClusterNodes;
import software.spool.api.route.ClusterRoutes;
import software.spool.core.adapter.http.JdkNodeHttpServer;
import software.spool.core.port.http.NodeHttpServer;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;

/** The Spool API: its HTTP server with the routes of the cluster, configured by {@code spool-api.yaml}. */
public final class SpoolApi {
    private static final String DEFAULT_CONFIGURATION = "spool-api.yaml";

    private final NodeHttpServer server;

    private SpoolApi(NodeHttpServer server) {
        this.server = server;
    }

    public static SpoolApi start(SpoolApiConfiguration configuration, Clock clock) throws IOException {
        NodeHttpServer server = new JdkNodeHttpServer(configuration.port());
        ClusterRoutes.of(new InMemoryClusterNodes(clock), new HeartbeatTimeout(configuration.heartbeatTimeout(), clock),
                configuration.authenticator(), configuration.roles()).forEach(server::register);
        server.start();
        return new SpoolApi(server);
    }

    public void stop() {
        server.stop();
    }

    /** Starts with the configuration in the file given, or {@code spool-api.yaml} in the working folder. */
    public static void main(String[] arguments) throws IOException {
        Path file = Path.of(arguments.length > 0 ? arguments[0] : DEFAULT_CONFIGURATION);
        SpoolApi api = start(SpoolApiConfiguration.read(file, System::getenv), Clock.systemUTC());
        Runtime.getRuntime().addShutdownHook(new Thread(api::stop));
    }
}
