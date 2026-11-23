package software.spool.api.route;

import software.spool.api.deployment.Deployer;
import software.spool.core.port.http.Caller;
import software.spool.core.port.http.HttpMethod;
import software.spool.core.port.http.HttpRequest;
import software.spool.core.port.http.HttpResponse;
import software.spool.core.port.http.HttpRoute;
import software.spool.core.port.http.HttpRouteHandler;
import software.spool.core.port.http.RouteAccess;

import java.util.Map;

/** {@code DELETE /spool/deployments/{name}}: withdraws a deployment, so no node runs it any more. */
final class WithdrawDeploymentRoute implements HttpRouteHandler {
    private static final String NAME = "name";

    private final Deployer deployer;

    WithdrawDeploymentRoute(Deployer deployer) {
        this.deployer = deployer;
    }

    HttpRoute route(RouteAccess access) {
        return new HttpRoute(HttpMethod.DELETE, DeployRoute.PATH, access, this).withMaxBodyBytes(0);
    }

    @Override
    public HttpResponse handle(HttpRequest request, Caller caller) {
        String name = request.pathParameters().get(NAME);
        return deployer.withdraw(name)
                ? new HttpResponse(204, Map.of(), new byte[0])
                : Responses.error(404, name + " is not deployed: list the deployments with GET " + ListDeploymentsRoute.PATH);
    }
}
