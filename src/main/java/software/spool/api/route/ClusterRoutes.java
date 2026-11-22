package software.spool.api.route;

import software.spool.api.node.ClusterNodes;
import software.spool.api.node.HeartbeatTimeout;
import software.spool.core.port.authentication.RequestAuthenticator;
import software.spool.core.port.http.HttpRoute;
import software.spool.core.port.http.RouteAccess;

import java.util.List;

/** The routes of the Spool API: nodes register and beat with the node role, and the list needs the read role. */
public final class ClusterRoutes {
    private ClusterRoutes() {}

    public static List<HttpRoute> of(ClusterNodes nodes, HeartbeatTimeout timeout, RequestAuthenticator authenticator,
                                     ClusterRoles roles) {
        RouteAccess node = new RouteAccess.Protected(authenticator, roles.node());
        RouteAccess read = new RouteAccess.Protected(authenticator, roles.read());
        return List.of(
                new RegisterNodeRoute(nodes, timeout).route(node),
                new NodeHeartbeatRoute(nodes).route(node),
                new ListNodesRoute(nodes, timeout).route(read),
                new OpenApiRoute().route());
    }
}
