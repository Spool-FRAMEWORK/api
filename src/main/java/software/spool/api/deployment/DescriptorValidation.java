package software.spool.api.deployment;

import java.util.Optional;

/** Checks the files of a pipeline before it is deployed, and gives what is wrong with them, if anything. */
@FunctionalInterface
public interface DescriptorValidation {
    Optional<String> problemOf(PipelineFiles files);
}
