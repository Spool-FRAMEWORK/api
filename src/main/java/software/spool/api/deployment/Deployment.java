package software.spool.api.deployment;

import java.util.Objects;
import java.util.Optional;

/**
 * A descriptor deployed in the cluster: its name, who sent it, its version, which goes up each time it is sent again,
 * and the node it is assigned to, if any live node could take it.
 */
public record Deployment(String name, DeploymentOwner owner, int version, String descriptor, Optional<String> node) {
    public Deployment {
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(owner, "owner is required");
        Objects.requireNonNull(descriptor, "descriptor is required");
        Objects.requireNonNull(node, "node is required");
        if (version < 1) throw new IllegalArgumentException("The version of a deployment starts at 1, got " + version);
    }

    public static Deployment first(String name, DeploymentOwner owner, String descriptor) {
        return new Deployment(name, owner, 1, descriptor, Optional.empty());
    }

    /** The next version, sent by {@code owner}, on the same node. */
    public Deployment replacedBy(DeploymentOwner sender, String newDescriptor) {
        return new Deployment(name, sender, version + 1, newDescriptor, node);
    }

    public Deployment assignedTo(Optional<String> newNode) {
        return new Deployment(name, owner, version, descriptor, newNode);
    }

    public boolean isOn(String nodeId) {
        return node.filter(nodeId::equals).isPresent();
    }
}
