Feature: The nodes of a cluster register in the Spool API, which lists them with their state

  A node in cluster mode registers with its id and its address and then sends heartbeats. The API lists every node it
  knows; one whose last heartbeat is older than the heartbeat timeout is down (ADR-022). Every route but the contract
  needs a caller identified by a provider of the authentication block, with the role the route asks for (ADR-013).

  Background:
    Given the Spool API with a heartbeat timeout of 30 seconds

  @DEV-107
  Scenario: A registered node is listed as up with its address
    When the node "node-a" registers with the address "http://10.0.0.1:8080"
    Then the API lists "node-a" as up at "http://10.0.0.1:8080"

  @DEV-107
  Scenario: A node that stops sending heartbeats is listed as down after the timeout
    Given the node "node-a" registers with the address "http://10.0.0.1:8080"
    When 31 seconds pass
    Then the API lists "node-a" as down

  @DEV-107
  Scenario: A heartbeat keeps a node up
    Given the node "node-a" registers with the address "http://10.0.0.1:8080"
    When 20 seconds pass
    And "node-a" sends a heartbeat
    And 20 seconds pass
    Then the API lists "node-a" as up at "http://10.0.0.1:8080"

  @DEV-107
  Scenario: A node that registers again, after a restart, keeps one entry with its new address
    Given the node "node-a" registers with the address "http://10.0.0.1:8080"
    When the node "node-a" registers with the address "http://10.0.0.7:8080"
    Then the API lists only one "node-a", up at "http://10.0.0.7:8080"

  @DEV-107
  Scenario: A heartbeat of a node that never registered is refused
    When "node-z" sends a heartbeat
    Then the API answers 404 with an error that says "node-z is not registered"

  @DEV-107
  Scenario Outline: A caller without a valid identity or without the role is refused
    When <caller> asks for the list of nodes
    Then the API answers <status>

    Examples:
      | caller                       | status |
      | a caller with no credentials | 401    |
      | a caller with a wrong signature | 401 |
      | a node, which cannot read    | 403    |

  @DEV-107
  Scenario: The contract of the API is open to anyone
    When a caller with no credentials asks for "/spool/openapi.yaml"
    Then the API answers 200 with the OpenAPI contract
