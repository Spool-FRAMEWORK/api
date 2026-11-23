package software.spool.api.route;

import software.spool.api.deployment.DeploymentAssigner;
import software.spool.api.deployment.Deployments;
import software.spool.core.port.http.Caller;
import software.spool.core.port.http.HttpMethod;
import software.spool.core.port.http.HttpRequest;
import software.spool.core.port.http.HttpResponse;
import software.spool.core.port.http.HttpRoute;
import software.spool.core.port.http.HttpRouteHandler;
import software.spool.core.port.http.RouteAccess;

import java.util.List;

/**
 * {@code GET /spool/deployments}: every deployment with the node it runs on, after bringing the assignments up to date
 * with the live nodes.
 */
final class ListDeploymentsRoute implements HttpRouteHandler {
    static final String PATH = "/spool/deployments";

    private final Deployments deployments;
    private final DeploymentAssigner assigner;

    ListDeploymentsRoute(Deployments deployments, DeploymentAssigner assigner) {
        this.deployments = deployments;
        this.assigner = assigner;
    }

    HttpRoute route(RouteAccess access) {
        return new HttpRoute(HttpMethod.GET, PATH, access, this);
    }

    @Override
    public HttpResponse handle(HttpRequest request, Caller caller) {
        assigner.assign();
        return Responses.json(200, new DeploymentsPayload(deployments.all().stream().map(DeploymentPayload::of).toList()));
    }

    record DeploymentsPayload(List<DeploymentPayload> deployments) {
    }
}
