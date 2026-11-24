Feature: A deployment is a main descriptor and the files it includes

  A pipeline can be split into files that include one another (ADR-016). It is sent to the Spool API as its main file
  and every other file with its path, relative to the main one. The API loads them together, as a node would load them
  from a folder, and keeps them together (ADR-036). A single YAML body is still a pipeline of one file.

  Background:
    Given the Spool API with a heartbeat timeout of 30 seconds
    And "ana" can deploy
    And the nodes "node-a" and "node-b" are up

  @DEV-145
  Scenario: A pipeline of several files is deployed, and its node gets every file
    When "ana" deploys "orders" as the main file "orders.spool.yaml" that includes "shared/infrastructure.yaml"
    Then the API answers 201 with the deployment "orders"
    And "node-a" gets the deployment "orders" with the files "orders.spool.yaml" and "shared/infrastructure.yaml"

  @DEV-145
  Scenario: A file that is included but not sent is refused, naming it
    When "ana" deploys "orders" as the main file "orders.spool.yaml" that includes "shared/infrastructure.yaml", without sending it
    Then the API answers 400 with an error that says "shared/infrastructure.yaml"
    And the API lists no deployments

  @DEV-145
  Scenario: A path that leaves the pipeline is refused
    When "ana" deploys "orders" with a file at the path "../secrets.yaml"
    Then the API answers 400 with an error that says "../secrets.yaml"
    And the API lists no deployments

  @DEV-145
  Scenario: A single YAML body is still a pipeline of one file
    When "ana" deploys the descriptor "orders"
    Then the API answers 201 with the deployment "orders"
    And "node-a" gets the deployment "orders" with the files "orders.spool.yaml"
