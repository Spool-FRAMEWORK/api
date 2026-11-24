Feature: A deployment runs only on live nodes that have every label it asks for

  Each node registers with its labels, such as cloud=aws or region=eu-west-1, and a deployment can ask for labels.
  A deployment goes only to a live node with every label it asks for, and one that asks for none can go to any live
  node. The labels replace the idea of a cluster: a cluster is the set of nodes with a label (ADR-036).

  Background:
    Given the Spool API with a heartbeat timeout of 30 seconds
    And "ana" can deploy

  @DEV-143
  Scenario: A node registers with its labels, and the API lists them
    When the node "node-a" registers with the address "http://10.0.0.1:8080" and the labels "cloud=aws,region=eu-west-1"
    Then the API lists the node "node-a" with the labels "cloud=aws,region=eu-west-1"

  @DEV-143
  Scenario: A deployment goes only to a live node with every label it asks for
    Given the node "node-a" is up with the labels "cloud=azure"
    And the node "node-b" is up with the labels "cloud=aws,region=eu-west-1"
    When "ana" deploys the descriptor "orders" asking for the labels "cloud=aws"
    Then the API lists "orders" on "node-b"
    And the API lists "orders" asking for the labels "cloud=aws"

  @DEV-143
  Scenario: A deployment that asks for no labels can go to any live node
    Given the node "node-a" is up with the labels "cloud=azure"
    When "ana" deploys the descriptor "orders"
    Then the API lists "orders" on "node-a"

  @DEV-143
  Scenario: A deployment with no live node with its labels waits, and the API says which labels it needs
    Given the node "node-a" is up with the labels "cloud=azure"
    When "ana" deploys the descriptor "orders" asking for the labels "cloud=aws"
    Then the API lists "orders" as waiting for a node with the labels "cloud=aws"

  @DEV-143
  Scenario: A waiting deployment goes to the first node with its labels that registers
    Given the node "node-a" is up with the labels "cloud=azure"
    And "ana" deployed the descriptor "orders" asking for the labels "cloud=aws"
    When the node "node-b" registers with the address "http://10.0.0.2:8080" and the labels "cloud=aws"
    Then the API lists "orders" on "node-b"

  @DEV-143
  Scenario: The deployment of a node that goes down moves only to another node with its labels
    Given the node "node-a" is up with the labels "cloud=aws"
    And the node "node-b" is up with the labels "cloud=azure"
    And the node "node-c" is up with the labels "cloud=aws"
    And "ana" deployed the descriptor "orders" asking for the labels "cloud=aws" on "node-a"
    When 31 seconds pass, with only "node-b" and "node-c" sending heartbeats
    Then the API lists "orders" on "node-c"

  @DEV-143
  Scenario: A node that registers again without a label moves away the deployments that ask for it
    Given the node "node-a" is up with the labels "cloud=aws"
    And the node "node-b" is up with the labels "cloud=aws"
    And "ana" deployed the descriptor "orders" asking for the labels "cloud=aws" on "node-a"
    When the node "node-a" registers with the address "http://10.0.0.1:8080" and the labels "cloud=azure"
    Then the API lists "orders" on "node-b"

  @DEV-143
  Scenario: Deploying again with labels its node does not have moves the deployment
    Given the node "node-a" is up with the labels "cloud=azure"
    And the node "node-b" is up with the labels "cloud=aws"
    And "ana" deployed the descriptor "orders" on "node-a"
    When "ana" deploys a new version of the descriptor "orders" asking for the labels "cloud=aws"
    Then the API lists "orders" on "node-b" in its version 2

  @DEV-143
  Scenario: A label not written as key=value is refused, and nothing is deployed
    When "ana" deploys the descriptor "orders" asking for the labels "aws"
    Then the API answers 400 with an error that says "key=value"
    And the API lists no deployments
