package software.spool.api.node;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/** A node the Spool API knows: its id, where it answers and when it last said it was alive. */
public record ClusterNode(String id, URI address, Instant lastHeartbeat) {
    public ClusterNode {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(address, "address is required");
        Objects.requireNonNull(lastHeartbeat, "lastHeartbeat is required");
    }

    public ClusterNode beatingAt(Instant moment) {
        return new ClusterNode(id, address, moment);
    }
}
