# Spool — API

[![License](https://img.shields.io/badge/License-AGPL%203.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/)

The **Spool API** manages a cluster of Spool nodes and is the single entry point to operate it. The nodes register in
it and send heartbeats, each with its labels, such as `cloud=aws`; a descriptor sent to it is deployed whole on the
live node with every label it asks for and the fewest deployments, and moves to another live node with those labels
when its node goes down or no longer has them. A deployment that no live node with its labels can take waits, and the
API lists it as `WAITING` with the labels it needs (ADR-036).

Decided in [ADR-022](https://linear.app/javier-castilla/document/adr-022-un-servicio-propio-la-api-de-spool-gestiona-un-cluster-de-e242fb8039c3)
and [ADR-031](https://linear.app/javier-castilla/document/adr-031-la-api-asigna-cada-despliegue-entero-a-un-nodo-y-el-nodo-544c4722ba57).

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
| `GET /spool/cluster/nodes/{id}/assignments` | node | The deployments of the node, each with its version and descriptor, and its lease in seconds |
| `PUT /spool/cluster/nodes/{id}/assignments/{name}` | node | The node says a deployment runs, `{"version": 1, "state": "RUNNING"}`, or failed, with `"state": "FAILED"` and an `error` |
| `PUT /spool/deployments/{name}` | deploy | Deploys a pipeline under the name: as JSON, `{"main": "orders.spool.yaml", "files": {...}}` with every file by its path relative to the main one, or as the YAML of a single file. `201` when new, `200` for a new version of it, `400` with the error of the DSL or a path that leaves the pipeline |
| `GET /spool/deployments` | read | Every deployment with its owner, version, node and state: `WAITING`, `STARTING`, `RUNNING` or `FAILED` with its error |
| `DELETE /spool/deployments/{name}` | deploy | Withdraws a deployment, so its node stops it |
| `PUT /spool/systems/{system}` | deploy | Creates a system, or gives it its shared files, `{"files": {"shared/infrastructure.yaml": "..."}}`. The pipelines that read a file that changes get a new version, and only they |
| `GET /spool/systems` | read | Every system with its shared files and its pipelines, their node, version and state |
| `GET /spool/systems/{system}` | read | One system, with the text of its shared files |
| `DELETE /spool/systems/{system}` | deploy | Withdraws the system and every pipeline of it |
| `PUT /spool/systems/{system}/pipelines/{pipeline}` | deploy | Deploys a pipeline in the system, as `{"main", "files"}` or a YAML; an include it does not send is looked up among the shared files |
| `GET /spool/systems/{system}/pipelines/{pipeline}` | read | A pipeline with its files as they were sent and the shared files it reads |
| `DELETE /spool/systems/{system}/pipelines/{pipeline}` | deploy | Withdraws a pipeline of the system |
| `GET /spool/openapi.yaml` | none | The contract |

A node whose last heartbeat is older than the heartbeat timeout is `DOWN`. A caller no provider can identify gets
`401`, and one without the role `403` (ADR-013).

A deployment goes whole to one node, so modules that share a piece of the process, such as an `IN_MEMORY` bus, stay
together. A new version stays on its node. A deployment with no live node waits for the first one that asks for its
assignments, and one on a node that is down moves to another live node; it starts there from what its infrastructure
keeps, and what was only in memory is done again. One that failed to start stays on its node until a new version is
sent or the node goes down. The API checks a descriptor as a node loads it, without building it; what only building
finds, such as a type without a plugin, the node reports as `FAILED`. Its `${env:NAME}` are read in the node.

Nodes and deployments are kept in memory: when the API restarts it forgets the deployments, and the nodes stop them
once their lease passes. Until there is a store that lasts, it is for development.

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
  roles: {node: node, read: cluster-reader, deploy: deployer}
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

Stop one node and, after the heartbeat timeout, it is listed `DOWN`.

4. Deploy a descriptor with a caller that has the deploy role, for example a third `HMAC_SIGNATURE` provider with
   `roles: deployer`:

   ```sh
   curl -X PUT http://localhost:8090/spool/deployments/orders -H "Content-Type: application/yaml" \
     --data-binary @orders.spool.yaml   # plus the signature headers of the provider
   ```

   `GET /spool/deployments` lists it on one of the two nodes, `RUNNING` once the node started it. Stop that node and,
   after the heartbeat timeout, it moves to the other one. `DELETE /spool/deployments/orders` stops it.

`LocalClusterTest` runs this same cluster, from registering to withdrawing a deployment.
