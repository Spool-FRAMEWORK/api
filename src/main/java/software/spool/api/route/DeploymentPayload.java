package software.spool.api.route;

import software.spool.api.deployment.Deployment;

import java.util.List;

/**
 * A deployment as the Spool API shows it, with the labels a node needs to run it, written as {@code key=value}
 * (ADR-036). While no live node with those labels can take it, {@code state} is {@value #WAITING} and {@code node} is
 * null; otherwise {@code state} is what its node said of it. {@code error} is null unless it failed.
 */
record DeploymentPayload(String name, String owner, String ownerProvider, int version, List<String> labels, String state,
                         String node, String error) {
    static final String WAITING = "WAITING";

    static DeploymentPayload of(Deployment deployment) {
        return new DeploymentPayload(deployment.name(), deployment.owner().subject(), deployment.owner().provider(),
                deployment.version(), deployment.labels().asList(),
                deployment.node().isPresent() ? deployment.progress().name() : WAITING,
                deployment.node().orElse(null), deployment.error().orElse(null));
    }
}
