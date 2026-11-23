package software.spool.api.route;

import software.spool.api.Cluster;
import software.spool.core.port.authentication.RequestAuthenticator;
import software.spool.core.port.http.HttpRoute;
import software.spool.core.port.http.RouteAccess;

import java.util.List;

/**
 * The routes of the Spool API: nodes register, beat, ask for their assignments and say how they went with the node
 * role, the lists need the read role, and
 * deploying and withdrawing need the deploy role.
 */
public final class ClusterRoutes {
    private ClusterRoutes() {}

    public static List<HttpRoute> of(Cluster cluster, RequestAuthenticator authenticator, ClusterRoles roles) {
        RouteAccess node = new RouteAccess.Protected(authenticator, roles.node());
        RouteAccess read = new RouteAccess.Protected(authenticator, roles.read());
        RouteAccess deploy = new RouteAccess.Protected(authenticator, roles.deploy());
        return List.of(
                new RegisterNodeRoute(cluster.nodes(), cluster.timeout()).route(node),
                new NodeHeartbeatRoute(cluster.nodes()).route(node),
                new NodeAssignmentsRoute(cluster.nodes(), cluster.timeout(), cluster.deployments(), cluster.assigner())
                        .route(node),
                new ReportAssignmentRoute(cluster.reports()).route(node),
                new ListNodesRoute(cluster.nodes(), cluster.timeout()).route(read),
                new DeployRoute(cluster.deployer()).route(deploy),
                new ListDeploymentsRoute(cluster.deployments(), cluster.assigner()).route(read),
                new WithdrawDeploymentRoute(cluster.deployer()).route(deploy),
                new OpenApiRoute().route());
    }
}
