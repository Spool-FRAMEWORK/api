package software.spool.api.deployment;

import software.spool.core.port.authentication.Identity;

import java.util.Objects;

/** Who sent a deployment: the subject and the provider that identified it (ADR-013). */
public record DeploymentOwner(String subject, String provider) {
    public DeploymentOwner {
        Objects.requireNonNull(subject, "subject is required");
        Objects.requireNonNull(provider, "provider is required");
    }

    public static DeploymentOwner of(Identity identity) {
        return new DeploymentOwner(identity.subject(), identity.provider());
    }
}
