package software.spool.api.route;

import software.spool.api.node.ClusterNodes;
import software.spool.core.port.http.Caller;
import software.spool.core.port.http.HttpMethod;
import software.spool.core.port.http.HttpRequest;
import software.spool.core.port.http.HttpResponse;
import software.spool.core.port.http.HttpRoute;
import software.spool.core.port.http.HttpRouteHandler;
import software.spool.core.port.http.RouteAccess;

import java.util.Map;

/** {@code PUT /spool/cluster/nodes/{id}/heartbeat}: a registered node says it is alive. */
final class NodeHeartbeatRoute implements HttpRouteHandler {
    static final String PATH = "/spool/cluster/nodes/{id}/heartbeat";
    private static final String ID = "id";

    private final ClusterNodes nodes;

    NodeHeartbeatRoute(ClusterNodes nodes) {
        this.nodes = nodes;
    }

    HttpRoute route(RouteAccess access) {
        return new HttpRoute(HttpMethod.PUT, PATH, access, this).withMaxBodyBytes(0);
    }

    @Override
    public HttpResponse handle(HttpRequest request, Caller caller) {
        String id = request.pathParameters().get(ID);
        return nodes.beat(id)
                .map(node -> new HttpResponse(204, Map.of(), new byte[0]))
                .orElseGet(() -> Responses.error(404, id + " is not registered: register it with POST " + RegisterNodeRoute.PATH));
    }
}
