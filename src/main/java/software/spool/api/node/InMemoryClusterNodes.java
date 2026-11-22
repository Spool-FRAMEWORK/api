package software.spool.api.node;

import java.net.URI;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** The nodes in the memory of the Spool API: they are lost when it restarts, and each node registers again. */
public final class InMemoryClusterNodes implements ClusterNodes {
    private final Map<String, ClusterNode> byId = new LinkedHashMap<>();
    private final Clock clock;

    public InMemoryClusterNodes(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public synchronized ClusterNode register(String id, URI address) {
        ClusterNode node = new ClusterNode(id, address, clock.instant());
        byId.put(id, node);
        return node;
    }

    @Override
    public synchronized Optional<ClusterNode> beat(String id) {
        return Optional.ofNullable(byId.computeIfPresent(id, (key, node) -> node.beatingAt(clock.instant())));
    }

    @Override
    public synchronized List<ClusterNode> all() {
        return List.copyOf(byId.values());
    }
}
