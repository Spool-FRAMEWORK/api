package software.spool.api.route;

import software.spool.api.deployment.Deployment;
import software.spool.api.deployment.DeploymentAssigner;
import software.spool.api.deployment.DeploymentOwner;
import software.spool.api.deployment.PipelineSystem;
import software.spool.api.deployment.SystemDeployer;
import software.spool.api.deployment.Systems;
import software.spool.core.adapter.jackson.PayloadDeserializerFactory;
import software.spool.core.port.http.Caller;
import software.spool.core.port.http.HttpMethod;
import software.spool.core.port.http.HttpRequest;
import software.spool.core.port.http.HttpResponse;
import software.spool.core.port.http.HttpRoute;
import software.spool.core.port.http.RouteAccess;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The routes of the systems and their pipelines (ADR-036): what can be done with the folder of a project, through
 * HTTP. One handler per route, each a small method of this class: they share the systems, the deployer and the
 * payloads.
 */
final class SystemRoutes {
    static final String SYSTEMS = "/spool/systems";
    static final String SYSTEM = SYSTEMS + "/{system}";
    static final String PIPELINE = SYSTEM + "/pipelines/{pipeline}";
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final HttpResponse NO_CONTENT = new HttpResponse(204, Map.of(), new byte[0]);

    private final Systems systems;
    private final SystemDeployer deployer;
    private final DeploymentAssigner assigner;

    SystemRoutes(Systems systems, SystemDeployer deployer, DeploymentAssigner assigner) {
        this.systems = systems;
        this.deployer = deployer;
        this.assigner = assigner;
    }

    List<HttpRoute> routes(RouteAccess read, RouteAccess deploy) {
        return List.of(
                new HttpRoute(HttpMethod.PUT, SYSTEM, deploy, this::putSystem).withMaxBodyBytes(MAX_BODY_BYTES),
                new HttpRoute(HttpMethod.GET, SYSTEMS, read, this::listSystems),
                new HttpRoute(HttpMethod.GET, SYSTEM, read, this::getSystem),
                new HttpRoute(HttpMethod.DELETE, SYSTEM, deploy, this::withdrawSystem).withMaxBodyBytes(0),
                new HttpRoute(HttpMethod.PUT, PIPELINE, deploy, this::putPipeline).withMaxBodyBytes(MAX_BODY_BYTES),
                new HttpRoute(HttpMethod.GET, PIPELINE, read, this::getPipeline),
                new HttpRoute(HttpMethod.DELETE, PIPELINE, deploy, this::withdrawPipeline).withMaxBodyBytes(0));
    }

    private HttpResponse putSystem(HttpRequest request, Caller caller) {
        Optional<Map<String, String>> files = sharedFilesOf(request);
        if (files.isEmpty()) return Responses.error(400, "The body must be {\"files\": {\"shared/infrastructure.yaml\": \"...\"}}, or empty");
        return switch (deployer.putSystem(system(request), files.get(), sender(caller))) {
            case SystemDeployer.Outcome.Done<PipelineSystem> done ->
                    Responses.json(done.isNew() ? 201 : 200, systemPayload(done.result()));
            case SystemDeployer.Outcome.Invalid<PipelineSystem> invalid -> Responses.error(400, invalid.problem());
            case SystemDeployer.Outcome.Missing<PipelineSystem> missing -> Responses.error(404, missing.problem());
        };
    }

    private HttpResponse listSystems(HttpRequest request, Caller caller) {
        assigner.assign();
        return Responses.json(200, new SystemPayloads.SystemsPayload(systems.all().stream().map(this::systemPayload).toList()));
    }

    private HttpResponse getSystem(HttpRequest request, Caller caller) {
        assigner.assign();
        return systems.find(system(request)).map(found -> Responses.json(200, systemPayload(found)))
                .orElseGet(() -> notFound(system(request)));
    }

    private HttpResponse withdrawSystem(HttpRequest request, Caller caller) {
        return deployer.withdrawSystem(system(request)).map(pipelines -> NO_CONTENT).orElseGet(() -> notFound(system(request)));
    }

    private HttpResponse putPipeline(HttpRequest request, Caller caller) {
        String pipeline = request.pathParameters().get("pipeline");
        return DeployRoute.filesOf(pipeline, request).map(files -> switch (deployer.putPipeline(system(request), pipeline, files, sender(caller))) {
            case SystemDeployer.Outcome.Done<Deployment> done ->
                    Responses.json(done.isNew() ? 201 : 200, SystemPayloads.PipelinePayload.of(done.result()));
            case SystemDeployer.Outcome.Invalid<Deployment> invalid -> Responses.error(400, invalid.problem());
            case SystemDeployer.Outcome.Missing<Deployment> missing -> Responses.error(404, missing.problem());
        }).orElseGet(() -> Responses.error(400, "A JSON body must be {\"main\": \"boe-crawler.spool.yaml\", \"files\": {...}}"));
    }

    private HttpResponse getPipeline(HttpRequest request, Caller caller) {
        assigner.assign();
        String pipeline = request.pathParameters().get("pipeline");
        return deployer.pipelinesOf(system(request)).stream().filter(found -> found.name().equals(pipeline)).findFirst()
                .map(found -> Responses.json(200, SystemPayloads.PipelineFilesPayload.of(found, deployer.sharedFilesReadBy(found))))
                .orElseGet(() -> Responses.error(404, pipeline + " is not a pipeline of " + system(request)));
    }

    private HttpResponse withdrawPipeline(HttpRequest request, Caller caller) {
        String pipeline = request.pathParameters().get("pipeline");
        return deployer.withdrawPipeline(system(request), pipeline) ? NO_CONTENT
                : Responses.error(404, pipeline + " is not a pipeline of " + system(request));
    }

    private SystemPayloads.SystemPayload systemPayload(PipelineSystem system) {
        return SystemPayloads.SystemPayload.of(system, deployer.pipelinesOf(system.name()));
    }

    private static Optional<Map<String, String>> sharedFilesOf(HttpRequest request) {
        if (request.body().length == 0) return Optional.of(Map.of());
        try {
            SystemPayloads.SharedFilesBody body = PayloadDeserializerFactory.json().as(SystemPayloads.SharedFilesBody.class)
                    .deserialize(request.body());
            return Optional.of(body.files() == null ? Map.of() : body.files());
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }

    private static String system(HttpRequest request) {
        return request.pathParameters().get("system");
    }

    private static DeploymentOwner sender(Caller caller) {
        return DeploymentOwner.of(((Caller.Identified) caller).identity());
    }

    private static HttpResponse notFound(String system) {
        return Responses.error(404, system + " does not exist: list the systems with GET " + SYSTEMS);
    }
}
