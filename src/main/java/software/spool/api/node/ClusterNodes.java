package software.spool.api.node;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * The nodes of the cluster and their last heartbeat, behind a port so the Spool API can keep them elsewhere, and
 * have replicas, without changing its model (ADR-022).
 */
public interface ClusterNodes {
    /** Registers a node, or registers it again with a new address, as alive now. */
    ClusterNode register(String id, URI address);

    /** Notes that a node is alive now, or gives nothing when it never registered. */
    Optional<ClusterNode> beat(String id);

    /** Every node, in the order they first registered. */
    List<ClusterNode> all();
}
