package software.spool.api.route;

import software.spool.core.port.authentication.Authentication;
import software.spool.core.port.authentication.RequestAuthenticator;
import software.spool.core.port.http.HttpRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Identifies the caller with the first provider that can, so nodes and people reach the same API with providers of
 * their own. When none can, the request is rejected with the reason of each.
 */
public final class FirstIdentifying implements RequestAuthenticator {
    private final List<RequestAuthenticator> providers;

    public FirstIdentifying(List<RequestAuthenticator> providers) {
        if (providers.isEmpty()) throw new IllegalArgumentException("The Spool API needs at least one authentication provider");
        this.providers = List.copyOf(providers);
    }

    @Override
    public Authentication authenticate(HttpRequest request) {
        List<String> reasons = new ArrayList<>();
        for (RequestAuthenticator provider : providers) {
            Authentication result = provider.authenticate(request);
            if (result instanceof Authentication.Accepted) return result;
            reasons.add(((Authentication.Rejected) result).reason());
        }
        return Authentication.rejected(String.join("; ", reasons));
    }
}
