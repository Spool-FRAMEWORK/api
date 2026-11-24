package software.spool.api.node;

import software.spool.core.model.spool.NodeLabels;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/** A node the Spool API knows: its id, where it answers, its labels (ADR-036) and when it last said it was alive. */
public record ClusterNode(String id, URI address, NodeLabels labels, Instant lastHeartbeat) {
    public ClusterNode {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(address, "address is required");
        Objects.requireNonNull(labels, "labels are required");
        Objects.requireNonNull(lastHeartbeat, "lastHeartbeat is required");
    }

    public ClusterNode beatingAt(Instant moment) {
        return new ClusterNode(id, address, labels, moment);
    }

    /** Whether the node has every label {@code asked}, so it can run a deployment that asks for them. */
    public boolean has(NodeLabels asked) {
        return labels.include(asked);
    }
}
