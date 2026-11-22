package software.spool.api.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import software.spool.api.node.HeartbeatTimeout;
import software.spool.api.node.InMemoryClusterNodes;
import software.spool.core.port.authentication.Authentication;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** The contract served at /spool/openapi.yaml lists exactly the routes the API registers. */
class ClusterRoutesContractTest {

    @Test
    void contract_listsEveryRegisteredRouteAndNoOther() throws IOException {
        Set<String> registered = ClusterRoutes.of(new InMemoryClusterNodes(Clock.systemUTC()),
                        new HeartbeatTimeout(Duration.ofSeconds(30), Clock.systemUTC()),
                        request -> Authentication.rejected("nobody"), new ClusterRoles("node", "read"))
                .stream().map(route -> route.method() + " " + route.path()).collect(Collectors.toSet());

        assertThat(contractRoutes()).isEqualTo(registered);
    }

    private static Set<String> contractRoutes() throws IOException {
        JsonNode paths = new ObjectMapper(new YAMLFactory()).readTree(OpenApiRoute.contract()).get("paths");
        Set<String> routes = new HashSet<>();
        paths.fields().forEachRemaining(path -> path.getValue().fieldNames()
                .forEachRemaining(method -> routes.add(method.toUpperCase(Locale.ROOT) + " " + path.getKey())));
        return routes;
    }
}
