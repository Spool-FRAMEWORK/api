package software.spool.api.deployment;

import software.spool.api.node.ClusterNode;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Picks, among the live nodes with every label the deployment asks for (ADR-036), the one with the fewest deployments;
 * between nodes with as many, the one with the smallest id.
 */
public final class LeastLoadedPlacement {
    public Optional<String> nodeFor(Deployment deployment, Collection<ClusterNode> liveNodes, List<Deployment> deployments) {
        return liveNodes.stream()
                .filter(node -> node.has(deployment.labels()))
                .map(ClusterNode::id)
                .min(Comparator.<String>comparingLong(node -> deploymentsOn(node, deployments))
                        .thenComparing(Comparator.naturalOrder()));
    }

    private static long deploymentsOn(String node, List<Deployment> deployments) {
        return deployments.stream().filter(deployment -> deployment.isOn(node)).count();
    }
}
