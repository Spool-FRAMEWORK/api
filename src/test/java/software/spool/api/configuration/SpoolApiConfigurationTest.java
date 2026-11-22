package software.spool.api.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.spool.api.route.ClusterRoles;
import software.spool.dsl.InvalidDescriptorException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpoolApiConfigurationTest {
    private static final String CONFIGURATION = """
            server: {port: 9191}
            authentication:
              nodes: {type: HMAC_SIGNATURE, configuration: {header: X-Spool-Signature, secret: "${env:SPOOL_CLUSTER_SECRET}", roles: node}}
            cluster:
              authentication: [nodes]
              heartbeatTimeout: 45s
              roles: {node: node, read: cluster-reader}
            """;

    @TempDir
    Path folder;

    @Test
    void read_completeFile_givesEveryValue() throws IOException {
        SpoolApiConfiguration configuration = SpoolApiConfiguration.read(write(CONFIGURATION),
                Map.of("SPOOL_CLUSTER_SECRET", "a-secret")::get);

        assertThat(configuration.port()).isEqualTo(9191);
        assertThat(configuration.heartbeatTimeout()).isEqualTo(Duration.ofSeconds(45));
        assertThat(configuration.roles()).isEqualTo(new ClusterRoles("node", "cluster-reader"));
    }

    @Test
    void read_providerThatIsNotDeclared_namesWhereItIsNamed() throws IOException {
        Path file = write(CONFIGURATION.replace("authentication: [nodes]", "authentication: [people]"));

        assertThatThrownBy(() -> SpoolApiConfiguration.read(file, Map.of("SPOOL_CLUSTER_SECRET", "a-secret")::get))
                .isInstanceOf(InvalidDescriptorException.class)
                .hasMessageContaining("cluster.authentication")
                .hasMessageContaining("people");
    }

    @Test
    void read_withoutRoles_saysTheyAreRequired() throws IOException {
        Path file = write(CONFIGURATION.replace("  roles: {node: node, read: cluster-reader}\n", ""));

        assertThatThrownBy(() -> SpoolApiConfiguration.read(file, Map.of("SPOOL_CLUSTER_SECRET", "a-secret")::get))
                .isInstanceOf(InvalidDescriptorException.class)
                .hasMessageContaining("cluster.roles");
    }

    private Path write(String content) throws IOException {
        return Files.writeString(folder.resolve("spool-api.yaml"), content);
    }
}
