package software.spool.api.deployment;

import java.util.List;
import java.util.Optional;

/** The systems of the cluster, behind a port so the Spool API can keep them elsewhere (ADR-036). */
public interface Systems {
    /** Keeps the system, replacing the one with its name. */
    void save(PipelineSystem system);

    Optional<PipelineSystem> find(String name);

    /** Takes the system out, and tells whether there was one with that name. */
    boolean remove(String name);

    /** Every system, in the order they were created. */
    List<PipelineSystem> all();
}
