package software.spool.api;

import software.spool.api.deployment.Deployer;
import software.spool.api.deployment.DeploymentAssigner;
import software.spool.api.deployment.Deployments;
import software.spool.api.deployment.DescriptorValidation;
import software.spool.api.deployment.InMemoryDeployments;
import software.spool.api.deployment.LeastLoadedPlacement;
import software.spool.api.deployment.NodeReports;
import software.spool.api.deployment.EffectiveFiles;
import software.spool.api.deployment.InMemorySystems;
import software.spool.api.deployment.SystemDeployer;
import software.spool.api.deployment.Systems;
import software.spool.api.node.ClusterNodes;
import software.spool.api.node.HeartbeatTimeout;
import software.spool.api.node.InMemoryClusterNodes;

import java.time.Clock;
import java.time.Duration;

/** What the Spool API keeps of the cluster, its nodes, its systems and its deployments, and what it does with them. */
public record Cluster(ClusterNodes nodes, HeartbeatTimeout timeout, Deployments deployments,
                      DeploymentAssigner assigner, Deployer deployer, NodeReports reports, Systems systems,
                      SystemDeployer systemDeployer, EffectiveFiles effectiveFiles) {

    /** A cluster kept in memory, lost when the API restarts. */
    public static Cluster inMemory(Duration heartbeatTimeout, DescriptorValidation validation, Clock clock) {
        ClusterNodes nodes = new InMemoryClusterNodes(clock);
        HeartbeatTimeout timeout = new HeartbeatTimeout(heartbeatTimeout, clock);
        Deployments deployments = new InMemoryDeployments();
        DeploymentAssigner assigner = new DeploymentAssigner(deployments, nodes, timeout, new LeastLoadedPlacement());
        Systems systems = new InMemorySystems();
        return new Cluster(nodes, timeout, deployments, assigner, new Deployer(deployments, validation, assigner),
                new NodeReports(deployments), systems, new SystemDeployer(systems, deployments, validation, assigner),
                new EffectiveFiles(systems));
    }
}
