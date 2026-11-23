package software.spool.api.route;

import software.spool.api.deployment.Deployment;

/**
 * A deployment as the Spool API shows it. While no live node can take it, {@code state} is {@value #WAITING} and
 * {@code node} is null; otherwise {@code state} is what its node said of it. {@code error} is null unless it failed.
 */
record DeploymentPayload(String name, String owner, String ownerProvider, int version, String state, String node,
                         String error) {
    static final String WAITING = "WAITING";

    static DeploymentPayload of(Deployment deployment) {
        return new DeploymentPayload(deployment.name(), deployment.owner().subject(), deployment.owner().provider(),
                deployment.version(), deployment.node().isPresent() ? deployment.progress().name() : WAITING,
                deployment.node().orElse(null), deployment.error().orElse(null));
    }
}
