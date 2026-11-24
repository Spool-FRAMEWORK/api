package software.spool.api.deployment;

import software.spool.api.node.ClusterNode;
import software.spool.api.node.ClusterNodes;
import software.spool.api.node.HeartbeatTimeout;
import software.spool.api.node.NodeState;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Brings the assignments up to date with the live nodes: a deployment without a node, on a node that is down, or on a
 * node that no longer has every label it asks for, goes to the live node the placement picks, one after the other, so
 * each choice sees the ones before it (ADR-031, ADR-036).
 */
public final class DeploymentAssigner {
    private final Deployments deployments;
    private final ClusterNodes nodes;
    private final HeartbeatTimeout timeout;
    private final LeastLoadedPlacement placement;

    public DeploymentAssigner(Deployments deployments, ClusterNodes nodes, HeartbeatTimeout timeout,
                              LeastLoadedPlacement placement) {
        this.deployments = deployments;
        this.nodes = nodes;
        this.timeout = timeout;
        this.placement = placement;
    }

    public synchronized void assign() {
        Map<String, ClusterNode> live = liveNodes();
        for (Deployment deployment : deployments.all()) {
            if (deployment.node().map(live::get).filter(node -> node.has(deployment.labels())).isPresent()) continue;
            List<Deployment> current = deployments.all();
            deployments.save(deployment.assignedTo(placement.nodeFor(deployment, live.values(), current)));
        }
    }

    private Map<String, ClusterNode> liveNodes() {
        return nodes.all().stream()
                .filter(node -> timeout.stateOf(node) == NodeState.UP)
                .collect(Collectors.toMap(ClusterNode::id, Function.identity()));
    }
}
