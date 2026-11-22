package software.spool.api.route;

import software.spool.core.adapter.jackson.RecordSerializerFactory;
import software.spool.core.port.http.HttpResponse;

/** The JSON answers of the routes of the Spool API. */
final class Responses {
    private Responses() {}

    static HttpResponse json(int status, Object body) {
        return HttpResponse.json(status, RecordSerializerFactory.record().serialize(body));
    }

    static HttpResponse error(int status, String message) {
        return json(status, new ErrorPayload(message));
    }

    record ErrorPayload(String error) {
    }
}
