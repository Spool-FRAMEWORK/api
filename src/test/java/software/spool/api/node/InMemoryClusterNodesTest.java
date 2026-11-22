package software.spool.api.node;

import java.time.Clock;

class InMemoryClusterNodesTest extends ClusterNodesContract {
    @Override
    protected ClusterNodes nodes(Clock clock) {
        return new InMemoryClusterNodes(clock);
    }
}
