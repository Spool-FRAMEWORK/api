package software.spool.api.deployment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The deployments in the memory of the Spool API: they are lost when it restarts. */
public final class InMemoryDeployments implements Deployments {
    private final Map<String, Deployment> byName = new LinkedHashMap<>();

    @Override
    public synchronized void save(Deployment deployment) {
        byName.put(deployment.name(), deployment);
    }

    @Override
    public synchronized Optional<Deployment> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    @Override
    public synchronized boolean remove(String name) {
        return byName.remove(name) != null;
    }

    @Override
    public synchronized List<Deployment> all() {
        return List.copyOf(byName.values());
    }
}
