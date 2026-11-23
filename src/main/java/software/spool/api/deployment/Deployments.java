package software.spool.api.deployment;

import java.util.List;
import java.util.Optional;

/** The deployments of the cluster, behind a port so the Spool API can keep them elsewhere (ADR-031). */
public interface Deployments {
    /** Keeps the deployment, replacing the one with its name. */
    void save(Deployment deployment);

    Optional<Deployment> find(String name);

    /** Takes the deployment out, and tells whether there was one with that name. */
    boolean remove(String name);

    /** Every deployment, in the order they were first sent. */
    List<Deployment> all();
}
