package software.spool.api.deployment;

import java.net.URI;
import java.util.Collection;
import java.util.Optional;

/** The paths of the files of a pipeline or a system: relative, and never out of the folder they describe (ADR-036). */
final class PipelinePaths {
    private PipelinePaths() {
    }

    /** The first path that leaves the folder, with why, if any. */
    static Optional<String> problemOf(Collection<String> paths) {
        return paths.stream().filter(path -> !inside(path)).sorted().findFirst()
                .map(path -> "The path " + path + " leaves the pipeline: give each file by its path relative to the main one");
    }

    private static boolean inside(String path) {
        if (path.isBlank() || path.startsWith("/") || path.startsWith("\\") || path.contains(":")) return false;
        String normalized = URI.create(path.replace('\\', '/')).normalize().toString();
        return !normalized.startsWith("../") && !normalized.equals("..");
    }
}
