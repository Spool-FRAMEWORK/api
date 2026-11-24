package software.spool.api.deployment;

import software.spool.dsl.DescriptorFiles;
import software.spool.dsl.source.InMemoryDescriptorSource;

import java.io.IOException;
import java.util.List;

/**
 * The files a node loads a deployment from: its own and the ones its system shares, as in the folder of a project
 * (ADR-036). A deployment of no system has only its own.
 */
public final class EffectiveFiles {
    private final Systems systems;

    public EffectiveFiles(Systems systems) {
        this.systems = systems;
    }

    public PipelineFiles of(Deployment deployment) {
        return deployment.system().flatMap(systems::find)
                .map(system -> deployment.files().with(system.sharedFiles()))
                .orElse(deployment.files());
    }

    /** The paths of the files the deployment reads, its own and the shared ones, following its includes. */
    public static List<String> readBy(PipelineFiles files) {
        try {
            return DescriptorFiles.of(new InMemoryDescriptorSource(files.files()), files.main());
        } catch (IOException unreadable) {
            return List.copyOf(files.files().keySet());
        }
    }
}
