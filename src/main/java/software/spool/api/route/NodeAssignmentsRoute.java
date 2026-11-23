package software.spool.api.route;

import software.spool.api.deployment.Deployment;
import software.spool.api.deployment.DeploymentAssigner;
import software.spool.api.deployment.Deployments;
import software.spool.api.node.ClusterNodes;
import software.spool.api.node.HeartbeatTimeout;
import software.spool.core.port.http.Caller;
import software.spool.core.port.http.HttpMethod;
import software.spool.core.port.http.HttpRequest;
import software.spool.core.port.http.HttpResponse;
import software.spool.core.port.http.HttpRoute;
import software.spool.core.port.http.HttpRouteHandler;
import software.spool.core.port.http.RouteAccess;

import java.util.List;

/**
 * {@code GET /spool/cluster/nodes/{id}/assignments}: the deployments assigned to a node, each with its version and
 * its descriptor, after bringing the assignments up to date, and for how long the node may keep running them without
 * the API: the heartbeat timeout (ADR-031).
 */
final class NodeAssignmentsRoute implements HttpRouteHandler {
    static final String PATH = "/spool/cluster/nodes/{id}/assignments";
    private static final String ID = "id";

    private final ClusterNodes nodes;
    private final HeartbeatTimeout timeout;
    private final Deployments deployments;
    private final DeploymentAssigner assigner;

    NodeAssignmentsRoute(ClusterNodes nodes, HeartbeatTimeout timeout, Deployments deployments, DeploymentAssigner assigner) {
        this.nodes = nodes;
        this.timeout = timeout;
        this.deployments = deployments;
        this.assigner = assigner;
    }

    HttpRoute route(RouteAccess access) {
        return new HttpRoute(HttpMethod.GET, PATH, access, this);
    }

    @Override
    public HttpResponse handle(HttpRequest request, Caller caller) {
        String id = request.pathParameters().get(ID);
        if (nodes.all().stream().noneMatch(node -> node.id().equals(id)))
            return Responses.error(404, id + " is not registered: register it with POST " + RegisterNodeRoute.PATH);
        assigner.assign();
        List<AssignmentPayload> assigned = deployments.all().stream().filter(deployment -> deployment.isOn(id))
                .map(AssignmentPayload::of).toList();
        return Responses.json(200, new AssignmentsPayload(timeout.duration().toSeconds(), assigned));
    }

    record AssignmentsPayload(long leaseSeconds, List<AssignmentPayload> deployments) {
    }

    record AssignmentPayload(String name, int version, String descriptor) {
        static AssignmentPayload of(Deployment deployment) {
            return new AssignmentPayload(deployment.name(), deployment.version(), deployment.descriptor());
        }
    }
}
