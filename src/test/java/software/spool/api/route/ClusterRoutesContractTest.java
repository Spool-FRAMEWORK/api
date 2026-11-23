package software.spool.api.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import software.spool.api.Cluster;
import software.spool.core.port.authentication.Authentication;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** The contract served at /spool/openapi.yaml lists exactly the routes the API registers. */
class ClusterRoutesContractTest {

    @Test
    void contract_listsEveryRegisteredRouteAndNoOther() throws IOException {
        Cluster cluster = Cluster.inMemory(Duration.ofSeconds(30), descriptor -> Optional.empty(), Clock.systemUTC());
        Set<String> registered = ClusterRoutes.of(cluster, request -> Authentication.rejected("nobody"),
                        new ClusterRoles("node", "read", "deploy"))
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
