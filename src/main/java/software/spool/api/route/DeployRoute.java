package software.spool.api.route;

import software.spool.api.deployment.Deployer;
import software.spool.api.deployment.DeploymentOwner;
import software.spool.core.port.http.Caller;
import software.spool.core.port.http.HttpMethod;
import software.spool.core.port.http.HttpRequest;
import software.spool.core.port.http.HttpResponse;
import software.spool.core.port.http.HttpRoute;
import software.spool.core.port.http.HttpRouteHandler;
import software.spool.core.port.http.RouteAccess;

import java.nio.charset.StandardCharsets;

/**
 * {@code PUT /spool/deployments/{name}}: deploys the descriptor of the body under the name, {@code 201} when it is new
 * and {@code 200} when it replaces the one with that name, or {@code 400} with the error of the DSL.
 */
final class DeployRoute implements HttpRouteHandler {
    static final String PATH = "/spool/deployments/{name}";
    private static final String NAME = "name";
    private static final int MAX_BODY_BYTES = 256 * 1024;

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
        Deployer.Outcome outcome = deployer.deploy(request.pathParameters().get(NAME),
                new String(request.body(), StandardCharsets.UTF_8), sender);
        return switch (outcome) {
            case Deployer.Outcome.Invalid invalid -> Responses.error(400, invalid.problem());
            case Deployer.Outcome.Deployed deployed ->
                    Responses.json(deployed.isNew() ? 201 : 200, DeploymentPayload.of(deployed.deployment()));
        };
    }
}
