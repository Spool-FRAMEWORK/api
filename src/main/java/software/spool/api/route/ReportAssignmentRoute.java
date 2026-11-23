package software.spool.api.route;

import software.spool.api.deployment.NodeReports;
import software.spool.core.adapter.jackson.PayloadDeserializerFactory;
import software.spool.core.port.http.Caller;
import software.spool.core.port.http.HttpMethod;
import software.spool.core.port.http.HttpRequest;
import software.spool.core.port.http.HttpResponse;
import software.spool.core.port.http.HttpRoute;
import software.spool.core.port.http.HttpRouteHandler;
import software.spool.core.port.http.RouteAccess;

import java.util.Map;
import java.util.Optional;

/**
 * {@code PUT /spool/cluster/nodes/{id}/assignments/{name}}: a node says whether a deployment assigned to it runs or
 * failed to start, for one version: {@code 204}, or {@code 409} when the deployment is on another node or in another
 * version, or {@code 404} when it is not deployed.
 */
final class ReportAssignmentRoute implements HttpRouteHandler {
    static final String PATH = "/spool/cluster/nodes/{id}/assignments/{name}";
    private static final int MAX_BODY_BYTES = 16 * 1024;
    private static final String RUNNING = "RUNNING";
    private static final String FAILED = "FAILED";

    private final NodeReports reports;

    ReportAssignmentRoute(NodeReports reports) {
        this.reports = reports;
    }

    HttpRoute route(RouteAccess access) {
        return new HttpRoute(HttpMethod.PUT, PATH, access, this).withMaxBodyBytes(MAX_BODY_BYTES);
    }

    @Override
    public HttpResponse handle(HttpRequest request, Caller caller) {
        Optional<Body> body = bodyOf(request.body());
        Optional<NodeReports.Report> report = body.flatMap(Body::report);
        if (report.isEmpty())
            return Responses.error(400, "The body must be {\"version\": 1, \"state\": \"RUNNING\"} or "
                    + "{\"version\": 1, \"state\": \"FAILED\", \"error\": \"...\"}");
        return switch (reports.report(request.pathParameters().get("id"), request.pathParameters().get("name"),
                body.get().version(), report.get())) {
            case NodeReports.Outcome.Accepted accepted -> new HttpResponse(204, Map.of(), new byte[0]);
            case NodeReports.Outcome.Refused refused -> Responses.error(409, refused.reason());
            case NodeReports.Outcome.Unknown unknown -> Responses.error(404, unknown.reason());
        };
    }

    private static Optional<Body> bodyOf(byte[] body) {
        try {
            return Optional.of(PayloadDeserializerFactory.json().as(Body.class).deserialize(body));
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }

    record Body(int version, String state, String error) {
        Optional<NodeReports.Report> report() {
            if (version < 1) return Optional.empty();
            if (RUNNING.equals(state)) return Optional.of(new NodeReports.Report.Running());
            if (FAILED.equals(state) && error != null && !error.isBlank())
                return Optional.of(new NodeReports.Report.Failed(error));
            return Optional.empty();
        }
    }
}
