package software.spool.api.deployment;

import java.util.Optional;

/**
 * Deploys and withdraws descriptors: a valid descriptor is kept under its name, a new version of the one already there
 * when the name is taken, and assigned to a node right away (ADR-031).
 */
public final class Deployer {
    private final Deployments deployments;
    private final DescriptorValidation validation;
    private final DeploymentAssigner assigner;

    public Deployer(Deployments deployments, DescriptorValidation validation, DeploymentAssigner assigner) {
        this.deployments = deployments;
        this.validation = validation;
        this.assigner = assigner;
    }

    public Outcome deploy(String name, String descriptor, DeploymentOwner sender) {
        Optional<String> problem = validation.problemOf(descriptor);
        if (problem.isPresent()) return new Outcome.Invalid(problem.get());
        Optional<Deployment> existing = deployments.find(name);
        deployments.save(existing.map(current -> current.replacedBy(sender, descriptor))
                .orElseGet(() -> Deployment.first(name, sender, descriptor)));
        assigner.assign();
        return new Outcome.Deployed(deployments.find(name).orElseThrow(), existing.isEmpty());
    }

    public boolean withdraw(String name) {
        return deployments.remove(name);
    }

    /** What came of sending a descriptor. */
    public sealed interface Outcome {
        /** The DSL found something wrong in the descriptor, and nothing was deployed. */
        record Invalid(String problem) implements Outcome {
        }

        /** The deployment as it is now, and whether it is new or replaced the one with its name. */
        record Deployed(Deployment deployment, boolean isNew) implements Outcome {
        }
    }
}
