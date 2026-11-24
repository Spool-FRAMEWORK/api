package software.spool.api.deployment;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A system (ADR-036): what is managed together, such as a product. It keeps once the files its pipelines share, each by
 * its path, such as {@code shared/infrastructure.yaml}, and who created it.
 */
public record PipelineSystem(String name, DeploymentOwner owner, Map<String, String> sharedFiles) {
    private static final Pattern NAME = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    public PipelineSystem {
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(owner, "owner is required");
        sharedFiles = Map.copyOf(sharedFiles);
    }

    /** Whether a name of a system or of a pipeline can go in a route: lower-case letters, digits and hyphens. */
    public static boolean isValidName(String name) {
        return NAME.matcher(name).matches();
    }

    public static String invalidName(String name) {
        return "The name '" + name + "' must have only lower-case letters, digits and hyphens, such as boe-crawler";
    }

    public PipelineSystem sharing(Map<String, String> files) {
        return new PipelineSystem(name, owner, files);
    }
}
