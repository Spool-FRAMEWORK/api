package software.spool.api.route;

import software.spool.api.node.ClusterNodes;
import software.spool.api.node.HeartbeatTimeout;
import software.spool.core.adapter.jackson.PayloadDeserializerFactory;
import software.spool.core.port.http.Caller;
import software.spool.core.port.http.HttpMethod;
import software.spool.core.port.http.HttpRequest;
import software.spool.core.port.http.HttpResponse;
import software.spool.core.port.http.HttpRoute;
import software.spool.core.port.http.HttpRouteHandler;
import software.spool.core.port.http.RouteAccess;

import java.net.URI;
import java.util.Optional;

/** {@code POST /spool/cluster/nodes}: a node registers, or registers again, with its id and its address. */
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
        return registrationOf(request.body())
                .map(registration -> Responses.json(201, NodePayload.of(nodes.register(registration.id(),
                        URI.create(registration.address())), timeout)))
                .orElseGet(() -> Responses.error(400, "The body must be {\"id\": \"...\", \"address\": \"http://...\"}"));
    }

    private static Optional<Registration> registrationOf(byte[] body) {
        try {
            return Optional.of(PayloadDeserializerFactory.json().as(Registration.class).deserialize(body))
                    .filter(Registration::isComplete);
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }

    record Registration(String id, String address) {
        boolean isComplete() {
            return id != null && !id.isBlank() && address != null && URI.create(address).isAbsolute();
        }
    }
}
