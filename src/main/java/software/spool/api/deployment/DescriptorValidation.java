package software.spool.api.deployment;

import java.util.Optional;

/** Checks a descriptor before it is deployed, and gives what is wrong with it, if anything. */
@FunctionalInterface
public interface DescriptorValidation {
    Optional<String> problemOf(String descriptor);
}
