package software.spool.api.deployment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Creates systems, changes their shared files and deploys and withdraws their pipelines (ADR-036), as one would with
 * the folder of a project. A change to a shared file gives a new version to every pipeline that reads it, and only to
 * them.
 */
public final class SystemDeployer {
    private final Systems systems;
    private final Deployments deployments;
    private final DescriptorValidation validation;
    private final DeploymentAssigner assigner;
    private final EffectiveFiles effective;

    public SystemDeployer(Systems systems, Deployments deployments, DescriptorValidation validation,
                          DeploymentAssigner assigner) {
        this.systems = systems;
        this.deployments = deployments;
        this.validation = validation;
        this.assigner = assigner;
        this.effective = new EffectiveFiles(systems);
    }

    /** Creates the system, or gives it these shared files, checking first every pipeline that reads one that changes. */
    public synchronized Outcome<PipelineSystem> putSystem(String name, Map<String, String> sharedFiles, DeploymentOwner sender) {
        if (!PipelineSystem.isValidName(name)) return new Outcome.Invalid<>(PipelineSystem.invalidName(name));
        Optional<String> badPath = PipelinePaths.problemOf(sharedFiles.keySet());
        if (badPath.isPresent()) return new Outcome.Invalid<>(badPath.get());
        Optional<PipelineSystem> existing = systems.find(name);
        PipelineSystem system = existing.map(current -> current.sharing(sharedFiles))
                .orElseGet(() -> new PipelineSystem(name, sender, sharedFiles));
        List<Deployment> touched = existing.map(current -> pipelinesReadingChanges(current, sharedFiles)).orElse(List.of());
        for (Deployment pipeline : touched) {
            Optional<String> problem = validation.problemOf(pipeline.files().with(sharedFiles));
            if (problem.isPresent()) return new Outcome.Invalid<>(pipeline.name() + " would stop loading: " + problem.get());
        }
        systems.save(system);
        touched.forEach(pipeline -> deployments.save(pipeline.replacedBy(sender, pipeline.files())));
        assigner.assign();
        return new Outcome.Done<>(system, existing.isEmpty());
    }

    /** Deploys a pipeline in an existing system, or a new version of it. */
    public synchronized Outcome<Deployment> putPipeline(String system, String name, PipelineFiles files, DeploymentOwner sender) {
        if (!PipelineSystem.isValidName(name)) return new Outcome.Invalid<>(PipelineSystem.invalidName(name));
        Optional<PipelineSystem> owning = systems.find(system);
        if (owning.isEmpty()) return new Outcome.Missing<>(system + " does not exist: create it with PUT /spool/systems/" + system);
        Optional<String> clash = files.files().keySet().stream().filter(owning.get().sharedFiles()::containsKey).sorted()
                .findFirst().map(path -> path + " is shared by " + system + ": change it there, or give the file another path");
        Optional<String> problem = files.problemWithThePaths().or(() -> clash)
                .or(() -> validation.problemOf(files.with(owning.get().sharedFiles())));
        if (problem.isPresent()) return new Outcome.Invalid<>(problem.get());
        String id = Deployment.idOf(Optional.of(system), name);
        Optional<Deployment> existing = deployments.find(id);
        deployments.save(existing.map(current -> current.replacedBy(sender, files))
                .orElseGet(() -> Deployment.first(Optional.of(system), name, sender, files)));
        assigner.assign();
        return new Outcome.Done<>(deployments.find(id).orElseThrow(), existing.isEmpty());
    }

    public synchronized boolean withdrawPipeline(String system, String name) {
        return deployments.remove(Deployment.idOf(Optional.of(system), name));
    }

    /** Withdraws the system and every pipeline of it, and tells how many pipelines it had, or nothing if it did not exist. */
    public synchronized Optional<Integer> withdrawSystem(String name) {
        if (systems.find(name).isEmpty()) return Optional.empty();
        List<Deployment> pipelines = pipelinesOf(name);
        pipelines.forEach(pipeline -> deployments.remove(pipeline.id()));
        systems.remove(name);
        return Optional.of(pipelines.size());
    }

    public List<Deployment> pipelinesOf(String system) {
        return deployments.all().stream().filter(deployment -> deployment.belongsTo(system)).toList();
    }

    /** The shared files of the system a pipeline reads, following its includes. */
    public List<String> sharedFilesReadBy(Deployment pipeline) {
        PipelineFiles all = effective.of(pipeline);
        return EffectiveFiles.readBy(all).stream().filter(path -> !pipeline.files().files().containsKey(path)).toList();
    }

    private List<Deployment> pipelinesReadingChanges(PipelineSystem current, Map<String, String> newFiles) {
        Map<String, String> changed = new HashMap<>(current.sharedFiles());
        newFiles.forEach((path, text) -> {
            if (Objects.equals(current.sharedFiles().get(path), text)) changed.remove(path);
            else changed.put(path, text);
        });
        return pipelinesOf(current.name()).stream()
                .filter(pipeline -> EffectiveFiles.readBy(effective.of(pipeline)).stream().anyMatch(changed::containsKey))
                .toList();
    }

    /** What came of a change: done (and whether it created something), refused with why, or about something missing. */
    public sealed interface Outcome<T> {
        record Done<T>(T result, boolean isNew) implements Outcome<T> {
        }

        record Invalid<T>(String problem) implements Outcome<T> {
        }

        record Missing<T>(String problem) implements Outcome<T> {
        }
    }
}
