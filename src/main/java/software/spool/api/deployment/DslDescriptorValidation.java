package software.spool.api.deployment;

import software.spool.dsl.DescriptorLoader;
import software.spool.dsl.source.TextDescriptorSource;

import java.io.IOException;
import java.util.Optional;

/**
 * Checks a descriptor with the DSL, as a node would load it, without building anything: building would connect to the
 * infrastructure, and the plugins and classes of the pipeline are in the nodes. What only building finds, such as a
 * type without a plugin or a module without a piece it needs, the node reports when it fails to start the deployment.
 * Its {@code ${env:NAME}} are read in the node, where the secrets are, so here each one stands for a value the API
 * does not know.
 */
public final class DslDescriptorValidation implements DescriptorValidation {
    @Override
    public Optional<String> problemOf(String descriptor) {
        try {
            new DescriptorLoader(new TextDescriptorSource(descriptor), DslDescriptorValidation::unknownValue)
                    .load(TextDescriptorSource.LOCATION, Optional.empty(), loaded -> loaded);
            return Optional.empty();
        } catch (IOException invalid) {
            return Optional.of(invalid.getMessage());
        }
    }

    private static String unknownValue(String variable) {
        if (variable.equals(DescriptorLoader.PROFILE_VARIABLE)) return null;
        return "${env:" + variable + "}";
    }
}
