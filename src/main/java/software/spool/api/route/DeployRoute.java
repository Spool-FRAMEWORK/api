package software.spool.api.route;

import software.spool.api.deployment.Deployer;
import software.spool.api.deployment.DeploymentOwner;
import software.spool.api.deployment.PipelineFiles;
import software.spool.core.adapter.jackson.PayloadDeserializerFactory;
import software.spool.core.port.http.Caller;
import software.spool.core.port.http.HttpMethod;
import software.spool.core.port.http.HttpRequest;
import software.spool.core.port.http.HttpResponse;
import software.spool.core.port.http.HttpRoute;
import software.spool.core.port.http.HttpRouteHandler;
import software.spool.core.port.http.RouteAccess;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * {@code PUT /spool/deployments/{name}}: deploys a pipeline under the name, {@code 201} when it is new and {@code 200}
 * when it replaces the one with that name, or {@code 400} with what is wrong. A JSON body gives the main file and every
 * other file by its path, {@code {"main": "orders.spool.yaml", "files": {...}}} (ADR-036); any other body is the YAML
 * of a pipeline of one file.
 */
final class DeployRoute implements HttpRouteHandler {
    static final String PATH = "/spool/deployments/{name}";
    private static final String NAME = "name";
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String JSON = "application/json";

    private final Deployer deployer;

    DeployRoute(Deployer deployer) {
        this.deployer = deployer;
    }

    HttpRoute route(RouteAccess access) {
        return new HttpRoute(HttpMethod.PUT, PATH, access, this).withMaxBodyBytes(MAX_BODY_BYTES);
    }

    @Override
    public HttpResponse handle(HttpRequest request, Caller caller) {
        DeploymentOwner sender = DeploymentOwner.of(((Caller.Identified) caller).identity());
        String name = request.pathParameters().get(NAME);
        Optional<PipelineFiles> files = filesOf(name, request);
        if (files.isEmpty())
            return Responses.error(400, "A JSON body must be {\"main\": \"orders.spool.yaml\", \"files\": {\"orders.spool.yaml\": \"...\"}}");
        return switch (deployer.deploy(name, files.get(), sender)) {
            case Deployer.Outcome.Invalid invalid -> Responses.error(400, invalid.problem());
            case Deployer.Outcome.Deployed deployed ->
                    Responses.json(deployed.isNew() ? 201 : 200, DeploymentPayload.of(deployed.deployment()));
        };
    }

    /** The pipeline of the body: a JSON of its files, or the YAML of a single file named after it. */
    static Optional<PipelineFiles> filesOf(String name, HttpRequest request) {
        if (!isJson(request)) return Optional.of(PipelineFiles.single(name, new String(request.body(), StandardCharsets.UTF_8)));
        try {
            FilesBody body = PayloadDeserializerFactory.json().as(FilesBody.class).deserialize(request.body());
            return body.main() == null || body.files() == null ? Optional.empty()
                    : Optional.of(new PipelineFiles(body.main(), body.files()));
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }

    private static boolean isJson(HttpRequest request) {
        return request.header(CONTENT_TYPE).filter(type -> type.toLowerCase(Locale.ROOT).startsWith(JSON)).isPresent();
    }

    record FilesBody(String main, Map<String, String> files) {
    }
}
