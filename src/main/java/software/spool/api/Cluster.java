package software.spool.api;

import software.spool.api.deployment.Deployer;
import software.spool.api.deployment.DeploymentAssigner;
import software.spool.api.deployment.Deployments;
import software.spool.api.deployment.DescriptorValidation;
import software.spool.api.deployment.InMemoryDeployments;
import software.spool.api.deployment.LeastLoadedPlacement;
import software.spool.api.node.ClusterNodes;
import software.spool.api.node.HeartbeatTimeout;
import software.spool.api.node.InMemoryClusterNodes;

import java.time.Clock;
import java.time.Duration;

/** What the Spool API keeps of the cluster, its nodes and its deployments, and what it does with them. */
public record Cluster(ClusterNodes nodes, HeartbeatTimeout timeout, Deployments deployments,
                      DeploymentAssigner assigner, Deployer deployer) {

    /** A cluster kept in memory, lost when the API restarts. */
    public static Cluster inMemory(Duration heartbeatTimeout, DescriptorValidation validation, Clock clock) {
        ClusterNodes nodes = new InMemoryClusterNodes(clock);
        HeartbeatTimeout timeout = new HeartbeatTimeout(heartbeatTimeout, clock);
        Deployments deployments = new InMemoryDeployments();
        DeploymentAssigner assigner = new DeploymentAssigner(deployments, nodes, timeout, new LeastLoadedPlacement());
        return new Cluster(nodes, timeout, deployments, assigner, new Deployer(deployments, validation, assigner));
    }
}
