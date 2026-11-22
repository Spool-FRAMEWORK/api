package software.spool.api.route;

import software.spool.core.port.http.Caller;
import software.spool.core.port.http.HttpMethod;
import software.spool.core.port.http.HttpRequest;
import software.spool.core.port.http.HttpResponse;
import software.spool.core.port.http.HttpRoute;
import software.spool.core.port.http.HttpRouteHandler;
import software.spool.core.port.http.RouteAccess;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

/** {@code GET /spool/openapi.yaml}: the contract of the API, open to anyone so they know what it offers. */
final class OpenApiRoute implements HttpRouteHandler {
    static final String PATH = "/spool/openapi.yaml";
    static final String CONTRACT = "/openapi/cluster-api.yaml";

    private final byte[] contract;

    OpenApiRoute() {
        this.contract = contract();
    }

    HttpRoute route() {
        return new HttpRoute(HttpMethod.GET, PATH, RouteAccess.open(), this);
    }

    @Override
    public HttpResponse handle(HttpRequest request, Caller caller) {
        return new HttpResponse(200, Map.of("Content-Type", "application/yaml"), contract);
    }

    static byte[] contract() {
        try (InputStream in = OpenApiRoute.class.getResourceAsStream(CONTRACT)) {
            if (in == null) throw new IllegalStateException("The contract " + CONTRACT + " is not in the classpath");
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
