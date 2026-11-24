package software.spool.api.deployment;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The files of a pipeline (ADR-036): the main descriptor and every other file, each by its path relative to the main
 * one, such as {@code shared/infrastructure.yaml}.
 */
public record PipelineFiles(String main, Map<String, String> files) {
    private static final String DESCRIPTOR_SUFFIX = ".spool.yaml";

    public PipelineFiles {
        Objects.requireNonNull(main, "main is required");
        files = Map.copyOf(files);
    }

    /** A pipeline of a single file, named after the deployment. */
    public static PipelineFiles single(String deployment, String descriptor) {
        String main = deployment + DESCRIPTOR_SUFFIX;
        return new PipelineFiles(main, Map.of(main, descriptor));
    }

    /** What is wrong with the paths, if anything: the main file is missing, or a path leaves the pipeline. */
    public Optional<String> problemWithThePaths() {
        if (!files.containsKey(main)) return Optional.of("The main file " + main + " is not among the files sent");
        return PipelinePaths.problemOf(files.keySet());
    }

    /** These files with the ones given, which never replace one of these. */
    public PipelineFiles with(Map<String, String> more) {
        Map<String, String> all = new HashMap<>(more);
        all.putAll(files);
        return new PipelineFiles(main, all);
    }
}
