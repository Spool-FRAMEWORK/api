# Spool — API

[![License](https://img.shields.io/badge/License-AGPL%203.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/)

The **Spool API** manages a cluster of Spool nodes and is the single entry point to operate it. Today the nodes
register in it, send heartbeats and are listed with their state; assigning them modules and deploying descriptors come
next.

Decided in [ADR-022](https://linear.app/javier-castilla/document/adr-022-un-servicio-propio-la-api-de-spool-gestiona-un-cluster-de-e242fb8039c3).

> Requires **Java 21+**.

---

## Routes

The contract is [`openapi/cluster-api.yaml`](src/main/resources/openapi/cluster-api.yaml), and the API serves it,
open to anyone, at `GET /spool/openapi.yaml`. A test keeps it equal to the routes.

| Route | Role | What it does |
|---|---|---|
| `POST /spool/cluster/nodes` | node | Registers a node, `{"id": "node-a", "address": "http://10.0.0.1:8080"}`, or registers it again with a new address |
| `PUT /spool/cluster/nodes/{id}/heartbeat` | node | Says that a registered node is alive; `404` if it never registered |
| `GET /spool/cluster/nodes` | read | Every node with its address, its state, `UP` or `DOWN`, and its last heartbeat |
| `GET /spool/openapi.yaml` | none | The contract |

A node whose last heartbeat is older than the heartbeat timeout is `DOWN`. A caller no provider can identify gets
`401`, and one without the role `403` (ADR-013). The nodes are kept in memory: when the API restarts, each node
registers again on its next start.

---

## Configuration

`spool-api.yaml`, in the working folder or given as the first argument:

```yaml
server: {port: 8090}                       # 8090 by default
authentication:                            # providers as in a descriptor (ADR-013)
  nodes:
    type: HMAC_SIGNATURE
    configuration: {header: X-Spool-Signature, timestampHeader: X-Spool-Timestamp, secret: "${env:SPOOL_CLUSTER_SECRET}", roles: node}
  people:
    type: OIDC
    configuration: {issuer: "https://login.example.com", audience: spool, rolesClaim: roles}
cluster:
  authentication: [nodes, people]          # the first one that identifies the caller wins
  heartbeatTimeout: 30s                    # 30s by default
  roles: {node: node, read: cluster-reader}
```

The nodes sign with the headers `X-Spool-Signature` and `X-Spool-Timestamp`, so their provider declares those two
and the secret of the cluster.

---

## A local cluster of two nodes

1. Start the API with the file above, without the `people` provider, and `SPOOL_CLUSTER_SECRET` set. Its main class
   is `software.spool.api.SpoolApi`, run with the jar of `spool-api` and its dependencies on the classpath.

2. Start two nodes, each the jar of your pipeline built with `spool-runtime`, in cluster mode:

   ```sh
   SPOOL_CLUSTER_API=http://localhost:8090 SPOOL_NODE_ID=node-a SPOOL_NODE_ADDRESS=http://localhost:8081 \
     SPOOL_CLUSTER_SECRET=change-me java -jar my-pipeline.jar
   SPOOL_CLUSTER_API=http://localhost:8090 SPOOL_NODE_ID=node-b SPOOL_NODE_ADDRESS=http://localhost:8082 \
     SPOOL_CLUSTER_SECRET=change-me java -jar my-pipeline.jar
   ```

   With the cluster variables a node does not load its descriptors: it starts empty, registers and sends a heartbeat
   every 10 seconds, or every `SPOOL_CLUSTER_HEARTBEAT`.

3. List them with a caller that has the read role, for example a second `HMAC_SIGNATURE` provider with its own secret
   and `roles: cluster-reader`, added to `cluster.authentication`. Both come `UP`:

   ```json
   {"nodes": [
     {"id": "node-a", "address": "http://localhost:8081", "state": "UP", "lastHeartbeat": "..."},
     {"id": "node-b", "address": "http://localhost:8082", "state": "UP", "lastHeartbeat": "..."}
   ]}
   ```

Stop one node and, after the heartbeat timeout, it is listed `DOWN`. `LocalClusterTest` runs this same cluster.
