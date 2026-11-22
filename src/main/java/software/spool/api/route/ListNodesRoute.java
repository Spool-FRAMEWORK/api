package software.spool.api.route;

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

/** {@code GET /spool/cluster/nodes}: every node the API knows, with its address and its state. */
final class ListNodesRoute implements HttpRouteHandler {
    static final String PATH = "/spool/cluster/nodes";

    private final ClusterNodes nodes;
    private final HeartbeatTimeout timeout;

    ListNodesRoute(ClusterNodes nodes, HeartbeatTimeout timeout) {
        this.nodes = nodes;
        this.timeout = timeout;
    }

    HttpRoute route(RouteAccess access) {
        return new HttpRoute(HttpMethod.GET, PATH, access, this);
    }

    @Override
    public HttpResponse handle(HttpRequest request, Caller caller) {
        return Responses.json(200, new NodesPayload(nodes.all().stream().map(node -> NodePayload.of(node, timeout)).toList()));
    }

    record NodesPayload(List<NodePayload> nodes) {
    }
}
