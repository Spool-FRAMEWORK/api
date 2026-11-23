package software.spool.api.route;

/**
 * The role a node needs to register and beat, the one a caller needs to read the cluster, and the one it needs to
 * deploy and withdraw descriptors.
 */
public record ClusterRoles(String node, String read, String deploy) {
}
