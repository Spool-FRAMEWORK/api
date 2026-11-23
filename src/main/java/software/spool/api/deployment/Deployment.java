package software.spool.api.deployment;

import java.util.Objects;
import java.util.Optional;

/**
 * A descriptor deployed in the cluster: its name, who sent it, its version, which goes up each time it is sent again,
 * the node it is assigned to, if any live node could take it, and what that node said of it. {@code error} is there
 * only when it failed.
 */
public record Deployment(String name, DeploymentOwner owner, int version, String descriptor, Optional<String> node,
                         Progress progress, Optional<String> error) {
    /** How a deployment assigned to a node is going: starting until the node says it runs or failed to start. */
    public enum Progress {
        STARTING, RUNNING, FAILED
    }

    public Deployment {
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(owner, "owner is required");
        Objects.requireNonNull(descriptor, "descriptor is required");
        Objects.requireNonNull(node, "node is required");
        Objects.requireNonNull(progress, "progress is required");
        Objects.requireNonNull(error, "error is required");
        if (version < 1) throw new IllegalArgumentException("The version of a deployment starts at 1, got " + version);
    }

    public static Deployment first(String name, DeploymentOwner owner, String descriptor) {
        return new Deployment(name, owner, 1, descriptor, Optional.empty(), Progress.STARTING, Optional.empty());
    }

    /** The next version, sent by {@code sender}, on the same node and starting again. */
    public Deployment replacedBy(DeploymentOwner sender, String newDescriptor) {
        return new Deployment(name, sender, version + 1, newDescriptor, node, Progress.STARTING, Optional.empty());
    }

    /** On another node, or on none, it starts again. */
    public Deployment assignedTo(Optional<String> newNode) {
        return new Deployment(name, owner, version, descriptor, newNode, Progress.STARTING, Optional.empty());
    }

    public Deployment running() {
        return new Deployment(name, owner, version, descriptor, node, Progress.RUNNING, Optional.empty());
    }

    public Deployment failedWith(String failure) {
        return new Deployment(name, owner, version, descriptor, node, Progress.FAILED, Optional.of(failure));
    }

    public boolean isOn(String nodeId) {
        return node.filter(nodeId::equals).isPresent();
    }
}
