Feature: A descriptor sent to the Spool API is deployed whole on a live node of the cluster

  A deployment has the name its sender gives it. The API validates its descriptor with the DSL, keeps it owned by
  whoever sent it, and assigns it
  whole to the live node with the fewest deployments; a tie goes to the node with the smallest id. A deployment with
  no live node waits until one registers, and the deployment of a node that goes down moves to another live node
  (ADR-031). Deploying and withdrawing need the deploy role.

  Background:
    Given the Spool API with a heartbeat timeout of 30 seconds
    And "ana" can deploy

  @DEV-94
  Scenario: A valid descriptor is deployed on the live node with the fewest deployments
    Given the nodes "node-a" and "node-b" are up
    And "node-a" already runs the deployment "payments"
    When "ana" deploys the descriptor "orders"
    Then the API answers 201 with the deployment "orders"
    And the API lists "orders" on "node-b", owned by "ana"

  @DEV-94
  Scenario: Between nodes with as many deployments, the one with the smallest id wins
    Given the nodes "node-b" and "node-a" are up
    When "ana" deploys the descriptor "orders"
    Then the API lists "orders" on "node-a"

  @DEV-94
  Scenario: An invalid descriptor is refused with the error of the DSL, and nothing is deployed
    Given the nodes "node-a" and "node-b" are up
    When "ana" deploys a descriptor whose piece "lake" has no kind
    Then the API answers 400 with an error that says "infrastructure.lake.kind"
    And the API lists no deployments

  @DEV-94
  Scenario: A deployment with no live node waits, and goes to the first node that registers
    When "ana" deploys the descriptor "orders"
    Then the API lists "orders" as waiting for a node
    When the node "node-a" registers with the address "http://10.0.0.1:8080"
    Then the API lists "orders" on "node-a"

  @DEV-94
  Scenario: The deployment of a node that goes down moves to another live node
    Given the nodes "node-a" and "node-b" are up
    And "ana" deployed the descriptor "orders" on "node-a"
    When 31 seconds pass, with only "node-b" sending heartbeats
    Then the API lists "orders" on "node-b"

  @DEV-94
  Scenario: A withdrawn deployment is no longer listed nor assigned to any node
    Given the nodes "node-a" and "node-b" are up
    And "ana" deployed the descriptor "orders" on "node-a"
    When "ana" withdraws the deployment "orders"
    Then the API answers 204
    And the API lists no deployments

  @DEV-94
  Scenario: Withdrawing a deployment that does not exist is refused
    When "ana" withdraws the deployment "unknown"
    Then the API answers 404 with an error that says "unknown is not deployed"

  @DEV-94
  Scenario: Deploying again with the same name replaces the descriptor and keeps the node
    Given the nodes "node-a" and "node-b" are up
    And "ana" deployed the descriptor "orders" on "node-a"
    When "ana" deploys a new version of the descriptor "orders"
    Then the API answers 200 with the deployment "orders"
    And the API lists "orders" on "node-a" in its version 2

  @DEV-94
  Scenario Outline: A caller without a valid identity or without the deploy role cannot deploy
    When <caller> deploys the descriptor "orders"
    Then the API answers <status>
    And the API lists no deployments

    Examples:
      | caller                       | status |
      | a caller with no credentials | 401    |
      | a node, which cannot deploy  | 403    |
