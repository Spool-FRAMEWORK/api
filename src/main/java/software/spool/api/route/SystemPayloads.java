package software.spool.api.route;

import software.spool.api.deployment.Deployment;
import software.spool.api.deployment.PipelineSystem;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Systems and their pipelines as the Spool API shows them (ADR-036). Nulls stand for what a pipeline does not have. */
final class SystemPayloads {
    private SystemPayloads() {
    }

    /** A system with its shared files, by their path and with their text, to read them back and change one. */
    record SystemPayload(String name, String owner, String ownerProvider, Map<String, String> sharedFiles,
                         List<PipelinePayload> pipelines) {
        static SystemPayload of(PipelineSystem system, List<Deployment> pipelines) {
            return new SystemPayload(system.name(), system.owner().subject(), system.owner().provider(),
                    new TreeMap<>(system.sharedFiles()), pipelines.stream().map(PipelinePayload::of).toList());
        }
    }

    record SystemsPayload(List<SystemPayload> systems) {
    }

    /** A pipeline in a list: where it runs and how it goes. */
    record PipelinePayload(String name, int version, String state, String node, String error) {
        static PipelinePayload of(Deployment pipeline) {
            DeploymentPayload shown = DeploymentPayload.of(pipeline);
            return new PipelinePayload(pipeline.name(), pipeline.version(), shown.state(), shown.node(), shown.error());
        }
    }

    /** A pipeline with its files, to read them back and change them. */
    record PipelineFilesPayload(String name, String system, String owner, int version, String state, String node,
                                String error, String main, Map<String, String> files, List<String> sharedFilesUsed) {
        static PipelineFilesPayload of(Deployment pipeline, List<String> sharedFilesUsed) {
            DeploymentPayload shown = DeploymentPayload.of(pipeline);
            return new PipelineFilesPayload(pipeline.name(), pipeline.system().orElse(null), pipeline.owner().subject(),
                    pipeline.version(), shown.state(), shown.node(), shown.error(), pipeline.files().main(),
                    pipeline.files().files(), sharedFilesUsed);
        }
    }

    record SharedFilesBody(Map<String, String> files) {
    }
}
