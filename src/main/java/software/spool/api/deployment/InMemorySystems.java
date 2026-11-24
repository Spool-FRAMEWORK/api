package software.spool.api.deployment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The systems in the memory of the Spool API: they are lost when it restarts. */
public final class InMemorySystems implements Systems {
    private final Map<String, PipelineSystem> byName = new LinkedHashMap<>();

    @Override
    public synchronized void save(PipelineSystem system) {
        byName.put(system.name(), system);
    }

    @Override
    public synchronized Optional<PipelineSystem> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    @Override
    public synchronized boolean remove(String name) {
        return byName.remove(name) != null;
    }

    @Override
    public synchronized List<PipelineSystem> all() {
        return List.copyOf(byName.values());
    }
}
