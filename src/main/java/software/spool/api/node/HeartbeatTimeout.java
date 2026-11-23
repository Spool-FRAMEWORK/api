package software.spool.api.node;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** Tells whether a node is up: its last heartbeat is not older than the timeout. */
public final class HeartbeatTimeout {
    private final Duration timeout;
    private final Clock clock;

    public HeartbeatTimeout(Duration timeout, Clock clock) {
        if (timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("The heartbeat timeout must be longer than zero, got " + timeout);
        this.timeout = timeout;
        this.clock = Objects.requireNonNull(clock);
    }

    public Duration duration() {
        return timeout;
    }

    public NodeState stateOf(ClusterNode node) {
        return node.lastHeartbeat().plus(timeout).isBefore(clock.instant()) ? NodeState.DOWN : NodeState.UP;
    }
}
