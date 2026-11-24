package software.spool.api.node;

import software.spool.core.model.spool.NodeLabels;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Rules every {@link ClusterNodes} adapter must follow. */
public abstract class ClusterNodesContract {
    private static final URI FIRST_ADDRESS = URI.create("http://10.0.0.1:8080");
    private static final URI SECOND_ADDRESS = URI.create("http://10.0.0.7:8080");

    protected final MovableClock clock = new MovableClock();

    protected abstract ClusterNodes nodes(Clock clock);

    @Test
    void register_newNode_isListedWithItsAddressAndNowAsItsLastHeartbeat() {
        ClusterNodes nodes = nodes(clock);

        nodes.register("node-a", FIRST_ADDRESS, NodeLabels.none());

        assertThat(nodes.all()).containsExactly(new ClusterNode("node-a", FIRST_ADDRESS, NodeLabels.none(), clock.instant()));
    }

    @Test
    void register_sameIdAgain_keepsOneEntryWithTheNewAddress() {
        ClusterNodes nodes = nodes(clock);
        nodes.register("node-a", FIRST_ADDRESS, NodeLabels.none());

        nodes.register("node-a", SECOND_ADDRESS, NodeLabels.none());

        assertThat(nodes.all()).singleElement().extracting(ClusterNode::address).isEqualTo(SECOND_ADDRESS);
    }

    @Test
    void register_sameIdAgainWithOtherLabels_keepsOnlyTheNewLabels() {
        ClusterNodes nodes = nodes(clock);
        nodes.register("node-a", FIRST_ADDRESS, NodeLabels.parse("cloud=aws,region=eu-west-1"));

        nodes.register("node-a", FIRST_ADDRESS, NodeLabels.parse("cloud=azure"));

        assertThat(nodes.all()).singleElement().extracting(ClusterNode::labels).isEqualTo(NodeLabels.parse("cloud=azure"));
    }

    @Test
    void beat_registeredNode_keepsItsLabels() {
        ClusterNodes nodes = nodes(clock);
        nodes.register("node-a", FIRST_ADDRESS, NodeLabels.parse("cloud=aws"));

        assertThat(nodes.beat("node-a")).map(ClusterNode::labels).contains(NodeLabels.parse("cloud=aws"));
    }

    @Test
    void beat_registeredNode_movesItsLastHeartbeatToNow() {
        ClusterNodes nodes = nodes(clock);
        nodes.register("node-a", FIRST_ADDRESS, NodeLabels.none());
        clock.advance(Duration.ofSeconds(20));

        assertThat(nodes.beat("node-a")).map(ClusterNode::lastHeartbeat).contains(clock.instant());
    }

    @Test
    void beat_nodeThatNeverRegistered_isEmptyAndRegistersNothing() {
        ClusterNodes nodes = nodes(clock);

        assertThat(nodes.beat("node-z")).isEmpty();
        assertThat(nodes.all()).isEmpty();
    }

    @Test
    void all_severalNodes_keepsTheOrderTheyFirstRegistered() {
        ClusterNodes nodes = nodes(clock);
        nodes.register("node-b", FIRST_ADDRESS, NodeLabels.none());
        nodes.register("node-a", SECOND_ADDRESS, NodeLabels.none());
        nodes.register("node-b", SECOND_ADDRESS, NodeLabels.none());

        assertThat(nodes.all()).extracting(ClusterNode::id).containsExactly("node-b", "node-a");
    }
}
