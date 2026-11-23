package software.spool.api.deployment;

/**
 * What a node says of a deployment assigned to it: that it runs, or that it failed to start and why. It counts only
 * for the version the deployment has now and only from the node it is on (ADR-031).
 */
public final class NodeReports {
    private final Deployments deployments;

    public NodeReports(Deployments deployments) {
        this.deployments = deployments;
    }

    public synchronized Outcome report(String node, String name, int version, Report report) {
        return deployments.find(name).<Outcome>map(deployment -> {
            if (!deployment.isOn(node)) return new Outcome.Refused(name + " is not on " + node);
            if (deployment.version() != version)
                return new Outcome.Refused(name + " is in version " + deployment.version() + ", not " + version);
            deployments.save(switch (report) {
                case Report.Running running -> deployment.running();
                case Report.Failed failed -> deployment.failedWith(failed.error());
            });
            return new Outcome.Accepted();
        }).orElseGet(() -> new Outcome.Unknown(name + " is not deployed"));
    }

    /** What the node says. */
    public sealed interface Report {
        record Running() implements Report {
        }

        record Failed(String error) implements Report {
        }
    }

    /** What came of it. */
    public sealed interface Outcome {
        record Accepted() implements Outcome {
        }

        /** The deployment is on another node or in another version: what the node says is out of date. */
        record Refused(String reason) implements Outcome {
        }

        record Unknown(String reason) implements Outcome {
        }
    }
}
