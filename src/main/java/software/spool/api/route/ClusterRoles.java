package software.spool.api.route;

/** The role a node needs to register and beat, and the one a caller needs to read the cluster. */
public record ClusterRoles(String node, String read) {
}
