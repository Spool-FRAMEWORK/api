Feature: A node asks the Spool API for its deployments and says how each one went

  With each heartbeat, a node in cluster mode asks the API for the deployments assigned to it, each with its version
  and its descriptor, and for how long it may keep running them without the API: the heartbeat timeout. Then it says
  whether each one runs or failed to start, with the error. A deployment that failed stays on its node and is not
  tried on another, unless that node goes down (ADR-031). These routes need the node role.

  Background:
    Given the Spool API with a heartbeat timeout of 30 seconds
    And "ana" can deploy
    And the nodes "node-a" and "node-b" are up

  @DEV-94
  Scenario: A node gets the deployments assigned to it, with their version and their descriptor
    Given "ana" deployed the descriptor "orders" on "node-a"
    When "node-a" asks for its assignments
    Then it gets the deployment "orders" in its version 1, with its descriptor
    And it may keep running them for 30 seconds without the API

  @DEV-94
  Scenario: A node with nothing assigned gets an empty list
    Given "ana" deployed the descriptor "orders" on "node-a"
    When "node-b" asks for its assignments
    Then it gets no deployments

  @DEV-94
  Scenario: A deployment that waited for a node is assigned when a node asks for its assignments
    Given 31 seconds pass
    And "ana" deployed the descriptor "orders", which waits for a node
    When the node "node-c" registers with the address "http://10.0.0.3:8080"
    And "node-c" asks for its assignments
    Then it gets the deployment "orders" in its version 1, with its descriptor

  @DEV-94
  Scenario: Until its node says how it went, a deployment is starting
    When "ana" deploys the descriptor "orders"
    Then the API lists "orders" as "STARTING"

  @DEV-94
  Scenario: A node says a deployment runs
    Given "ana" deployed the descriptor "orders" on "node-a"
    When "node-a" says "orders" in its version 1 runs
    Then the API answers 204
    And the API lists "orders" as "RUNNING"

  @DEV-94
  Scenario: A deployment that failed to start shows the error and stays on its node
    Given "ana" deployed the descriptor "orders" on "node-a"
    When "node-a" says "orders" in its version 1 failed with "type SIDEWAYS has no plugin"
    Then the API lists "orders" as "FAILED" with the error "type SIDEWAYS has no plugin"
    And the API lists "orders" on "node-a"

  @DEV-94
  Scenario: A new version starts again, whatever the last one did
    Given "ana" deployed the descriptor "orders" on "node-a"
    And "node-a" said "orders" in its version 1 failed with "type SIDEWAYS has no plugin"
    When "ana" deploys a new version of the descriptor "orders"
    Then the API lists "orders" as "STARTING"

  @DEV-94
  Scenario: What a node says of an old version, or of a deployment that is not on it, is refused
    Given "ana" deployed the descriptor "orders" on "node-a"
    And "ana" deploys a new version of the descriptor "orders"
    When "node-a" says "orders" in its version 1 runs
    Then the API answers 409 with an error that says "version 2"
    When "node-b" says "orders" in its version 2 runs
    Then the API answers 409 with an error that says "not on node-b"
    And the API lists "orders" as "STARTING"

  @DEV-94
  Scenario: A node that never registered cannot ask for assignments
    When "node-z" asks for its assignments
    Then the API answers 404 with an error that says "node-z is not registered"

  @DEV-94
  Scenario: Only a node can ask for assignments
    When a reader asks for the assignments of "node-a"
    Then the API answers 403
