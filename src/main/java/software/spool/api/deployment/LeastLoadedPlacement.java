package software.spool.api.deployment;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Picks the live node with the fewest deployments; between nodes with as many, the one with the smallest id. */
public final class LeastLoadedPlacement {
    public Optional<String> nodeFor(Collection<String> liveNodes, List<Deployment> deployments) {
        return liveNodes.stream()
                .min(Comparator.<String>comparingLong(node -> deploymentsOn(node, deployments))
                        .thenComparing(Comparator.naturalOrder()));
    }

    private static long deploymentsOn(String node, List<Deployment> deployments) {
        return deployments.stream().filter(deployment -> deployment.isOn(node)).count();
    }
}
