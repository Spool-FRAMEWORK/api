package software.spool.api.route;

import software.spool.api.node.ClusterNode;
import software.spool.api.node.HeartbeatTimeout;
import software.spool.api.node.NodeState;

import java.util.List;

/** A node as the Spool API shows it, with its labels written as {@code key=value} (ADR-036). */
record NodePayload(String id, String address, List<String> labels, NodeState state, String lastHeartbeat) {
    static NodePayload of(ClusterNode node, HeartbeatTimeout timeout) {
        return new NodePayload(node.id(), node.address().toString(), node.labels().asList(), timeout.stateOf(node),
                node.lastHeartbeat().toString());
    }
}
