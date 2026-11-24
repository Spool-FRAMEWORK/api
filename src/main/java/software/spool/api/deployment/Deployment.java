package software.spool.api.deployment;

import software.spool.core.model.spool.NodeLabels;

import java.util.Objects;
import java.util.Optional;

/**
 * A pipeline deployed in the cluster, as its files (ADR-036): its name, the system it belongs to, if any, who sent it,
 * its version, which goes up each time it changes, the labels a node needs to run it, the node it is assigned to, if
 * any live node with those labels could take it, and what that node said of it. {@code error} is there only when it
 * failed.
 */
public record Deployment(String name, Optional<String> system, DeploymentOwner owner, int version, PipelineFiles files,
                         NodeLabels labels, Optional<String> node, Progress progress, Optional<String> error) {
    private static final String SYSTEM_SEPARATOR = ".";

    /** How a deployment assigned to a node is going: starting until the node says it runs or failed to start. */
    public enum Progress {
        STARTING, RUNNING, FAILED
    }

    public Deployment {
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(system, "system is required");
        Objects.requireNonNull(owner, "owner is required");
        Objects.requireNonNull(files, "files are required");
        Objects.requireNonNull(labels, "labels are required");
        Objects.requireNonNull(node, "node is required");
        Objects.requireNonNull(progress, "progress is required");
        Objects.requireNonNull(error, "error is required");
        if (version < 1) throw new IllegalArgumentException("The version of a deployment starts at 1, got " + version);
    }

    public static Deployment first(String name, DeploymentOwner owner, PipelineFiles files) {
        return first(Optional.empty(), name, owner, files, NodeLabels.none());
    }

    public static Deployment first(Optional<String> system, String name, DeploymentOwner owner, PipelineFiles files) {
        return first(system, name, owner, files, NodeLabels.none());
    }

    public static Deployment first(Optional<String> system, String name, DeploymentOwner owner, PipelineFiles files,
                                   NodeLabels labels) {
        return new Deployment(name, system, owner, 1, files, labels, Optional.empty(), Progress.STARTING, Optional.empty());
    }

    /** The key of the deployment among all of them: its name, after its system and a dot when it has one. */
    public static String idOf(Optional<String> system, String name) {
        return system.map(owning -> owning + SYSTEM_SEPARATOR + name).orElse(name);
    }

    public String id() {
        return idOf(system, name);
    }

    /** The next version, sent by {@code sender}, with the same labels, on the same node and starting again. */
    public Deployment replacedBy(DeploymentOwner sender, PipelineFiles newFiles) {
        return replacedBy(sender, newFiles, labels);
    }

    /** The next version, sent by {@code sender}, on the same node and starting again, until it is assigned again. */
    public Deployment replacedBy(DeploymentOwner sender, PipelineFiles newFiles, NodeLabels newLabels) {
        return new Deployment(name, system, sender, version + 1, newFiles, newLabels, node, Progress.STARTING, Optional.empty());
    }

    /** On another node, or on none, it starts again. */
    public Deployment assignedTo(Optional<String> newNode) {
        return new Deployment(name, system, owner, version, files, labels, newNode, Progress.STARTING, Optional.empty());
    }

    public Deployment running() {
        return new Deployment(name, system, owner, version, files, labels, node, Progress.RUNNING, Optional.empty());
    }

    public Deployment failedWith(String failure) {
        return new Deployment(name, system, owner, version, files, labels, node, Progress.FAILED, Optional.of(failure));
    }

    public boolean isOn(String nodeId) {
        return node.filter(nodeId::equals).isPresent();
    }

    public boolean belongsTo(String systemName) {
        return system.filter(systemName::equals).isPresent();
    }
}
