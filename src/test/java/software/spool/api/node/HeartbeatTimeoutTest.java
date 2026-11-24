package software.spool.api.node;

import software.spool.core.model.spool.NodeLabels;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeartbeatTimeoutTest {
    private final MovableClock clock = new MovableClock();
    private final HeartbeatTimeout timeout = new HeartbeatTimeout(Duration.ofSeconds(30), clock);
    private final ClusterNode node = new ClusterNode("node-a", URI.create("http://10.0.0.1:8080"), NodeLabels.none(), clock.instant());

    @Test
    void stateOf_heartbeatExactlyAsOldAsTheTimeout_isUp() {
        clock.advance(Duration.ofSeconds(30));

        assertThat(timeout.stateOf(node)).isEqualTo(NodeState.UP);
    }

    @Test
    void stateOf_heartbeatOlderThanTheTimeout_isDown() {
        clock.advance(Duration.ofSeconds(31));

        assertThat(timeout.stateOf(node)).isEqualTo(NodeState.DOWN);
    }

    @Test
    void new_timeoutOfZero_isRejected() {
        assertThatThrownBy(() -> new HeartbeatTimeout(Duration.ZERO, clock))
                .hasMessage("The heartbeat timeout must be longer than zero, got PT0S");
    }
}
