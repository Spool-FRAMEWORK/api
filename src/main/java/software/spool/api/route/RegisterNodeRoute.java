package software.spool.api.route;

import software.spool.api.node.ClusterNodes;
import software.spool.api.node.HeartbeatTimeout;
import software.spool.core.adapter.jackson.PayloadDeserializerFactory;
import software.spool.core.model.spool.NodeLabels;
import software.spool.core.port.http.Caller;
import software.spool.core.port.http.HttpMethod;
import software.spool.core.port.http.HttpRequest;
import software.spool.core.port.http.HttpResponse;
import software.spool.core.port.http.HttpRoute;
import software.spool.core.port.http.HttpRouteHandler;
import software.spool.core.port.http.RouteAccess;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * {@code POST /spool/cluster/nodes}: a node registers, or registers again, with its id, its address and its labels,
 * each written as {@code key=value} (ADR-036). A node that sends no labels has none.
 */
final class RegisterNodeRoute implements HttpRouteHandler {
    static final String PATH = "/spool/cluster/nodes";
    private static final int MAX_BODY_BYTES = 4096;

    private final ClusterNodes nodes;
    private final HeartbeatTimeout timeout;

    RegisterNodeRoute(ClusterNodes nodes, HeartbeatTimeout timeout) {
        this.nodes = nodes;
        this.timeout = timeout;
    }

    HttpRoute route(RouteAccess access) {
        return new HttpRoute(HttpMethod.POST, PATH, access, this).withMaxBodyBytes(MAX_BODY_BYTES);
    }

    @Override
    public HttpResponse handle(HttpRequest request, Caller caller) {
        Optional<Registration> registration = registrationOf(request.body());
        if (registration.isEmpty())
            return Responses.error(400, "The body must be {\"id\": \"...\", \"address\": \"http://...\", \"labels\": [\"key=value\"]}");
        NodeLabels labels;
        try {
            labels = registration.get().nodeLabels();
        } catch (IllegalArgumentException badLabel) {
            return Responses.error(400, badLabel.getMessage());
        }
        return Responses.json(201, NodePayload.of(nodes.register(registration.get().id(),
                URI.create(registration.get().address()), labels), timeout));
    }

    private static Optional<Registration> registrationOf(byte[] body) {
        try {
            return Optional.of(PayloadDeserializerFactory.json().as(Registration.class).deserialize(body))
                    .filter(Registration::isComplete);
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }

    record Registration(String id, String address, List<String> labels) {
        boolean isComplete() {
            return id != null && !id.isBlank() && address != null && URI.create(address).isAbsolute();
        }

        NodeLabels nodeLabels() {
            return labels == null ? NodeLabels.none() : NodeLabels.of(labels);
        }
    }
}
