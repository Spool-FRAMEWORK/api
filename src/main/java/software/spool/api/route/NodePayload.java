package software.spool.api.route;

import software.spool.api.node.ClusterNode;
import software.spool.api.node.HeartbeatTimeout;
import software.spool.api.node.NodeState;

/** A node as the Spool API shows it. */
record NodePayload(String id, String address, NodeState state, String lastHeartbeat) {
    static NodePayload of(ClusterNode node, HeartbeatTimeout timeout) {
        return new NodePayload(node.id(), node.address().toString(), timeout.stateOf(node), node.lastHeartbeat().toString());
    }
}
