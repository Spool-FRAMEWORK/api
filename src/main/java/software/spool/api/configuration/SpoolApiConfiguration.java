package software.spool.api.configuration;

import software.spool.api.route.ClusterRoles;
import software.spool.api.route.FirstIdentifying;
import software.spool.core.port.authentication.RequestAuthenticator;
import software.spool.dsl.descriptors.authentication.AuthenticationDescriptor;
import software.spool.dsl.providers.authentication.RequestAuthenticators;
import software.spool.dsl.reader.DescriptorReader;
import software.spool.dsl.reader.ValueParsers;
import software.spool.dsl.variables.DescriptorVariables;
import software.spool.dsl.variables.EnvironmentValues;
import software.spool.dsl.yaml.YamlTreeParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * What {@code spool-api.yaml} says: the port, the providers of the {@code authentication} block, read as in a
 * descriptor (ADR-013), which of them identify the callers of the API, the heartbeat timeout and the roles. Variables
 * of the environment are written as in a descriptor, {@code ${env:NAME}}.
 */
public record SpoolApiConfiguration(int port, RequestAuthenticator authenticator, Duration heartbeatTimeout,
                                    ClusterRoles roles) {
    private static final int DEFAULT_PORT = 8090;
    private static final Duration DEFAULT_HEARTBEAT_TIMEOUT = Duration.ofSeconds(30);

    public static SpoolApiConfiguration read(Path file, UnaryOperator<String> environment) throws IOException {
        Map<String, Object> tree = new YamlTreeParser().deserialize(Files.readAllBytes(file));
        DescriptorReader root = DescriptorReader.of("", new DescriptorVariables(environment, new EnvironmentValues()).resolve(tree));
        DescriptorReader cluster = root.object("cluster");
        DescriptorReader roles = cluster.object("roles");
        return new SpoolApiConfiguration(
                root.objectOrEmpty("server").optional("port", ValueParsers.INTEGER).orElse(DEFAULT_PORT),
                authenticator(providers(root.object("authentication")), cluster),
                cluster.optional("heartbeatTimeout", ValueParsers.DURATION).orElse(DEFAULT_HEARTBEAT_TIMEOUT),
                new ClusterRoles(roles.string("node"), roles.string("read"), roles.string("deploy")));
    }

    private static RequestAuthenticators providers(DescriptorReader block) {
        Map<String, AuthenticationDescriptor> providers = new LinkedHashMap<>();
        block.keys().forEach(name -> {
            DescriptorReader provider = block.object(name);
            providers.put(name, new AuthenticationDescriptor(provider.string("type"), provider.stringMap("configuration")));
        });
        return RequestAuthenticators.from(providers);
    }

    private static RequestAuthenticator authenticator(RequestAuthenticators providers, DescriptorReader cluster) {
        List<String> names = cluster.required("authentication", ValueParsers.TEXTS);
        return new FirstIdentifying(names.stream().map(name -> providers.find(name, "cluster.authentication")).toList());
    }
}
