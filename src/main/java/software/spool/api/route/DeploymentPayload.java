package software.spool.api.route;

import software.spool.api.deployment.Deployment;

/**
 * A deployment as the Spool API shows it. {@code node} is null, and {@code state} {@value #WAITING}, while no live node
 * can take it.
 */
record DeploymentPayload(String name, String owner, String ownerProvider, int version, String state, String node) {
    static final String WAITING = "WAITING";
    static final String ASSIGNED = "ASSIGNED";

    static DeploymentPayload of(Deployment deployment) {
        return new DeploymentPayload(deployment.name(), deployment.owner().subject(), deployment.owner().provider(),
                deployment.version(), deployment.node().isPresent() ? ASSIGNED : WAITING, deployment.node().orElse(null));
    }
}
