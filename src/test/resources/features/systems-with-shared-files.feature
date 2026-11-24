Feature: Pipelines are grouped in systems, which keep the files their pipelines share

  A system is what is managed together, such as a product. It keeps its shared files, such as its infrastructure, once,
  and its pipelines include them by their path, as in the folder of a project. Everything that can be done with that
  folder can be done through the API: create, change one file, read the files back, see the state and withdraw
  (ADR-036).

  Background:
    Given the Spool API with a heartbeat timeout of 30 seconds
    And "ana" can deploy
    And the nodes "node-a" and "node-b" are up

  @DEV-146
  Scenario: A pipeline includes a file its system shares, and its node gets both
    Given "ana" created the system "boe" with the shared file "shared/infrastructure.yaml"
    When "ana" deploys the pipeline "boe-crawler" of "boe", whose main file includes "shared/infrastructure.yaml"
    Then the API answers 201
    And the node of "boe-crawler" gets its main file and "shared/infrastructure.yaml"

  @DEV-146
  Scenario: A pipeline of a system that does not exist is refused
    When "ana" deploys the pipeline "boe-crawler" of "boe", whose main file includes "shared/infrastructure.yaml"
    Then the API answers 404 with an error that says "boe does not exist"

  @DEV-146
  Scenario: An include that neither the pipeline nor its system has is refused, naming the file
    Given "ana" created the system "boe" with the shared file "shared/infrastructure.yaml"
    When "ana" deploys the pipeline "boe-crawler" of "boe", whose main file includes "shared/missing.yaml"
    Then the API answers 400 with an error that says "shared/missing.yaml"

  @DEV-146
  Scenario: A pipeline cannot bring its own version of a shared file
    Given "ana" created the system "boe" with the shared file "shared/infrastructure.yaml"
    When "ana" deploys the pipeline "boe-crawler" of "boe" with its own "shared/infrastructure.yaml"
    Then the API answers 400 with an error that says "shared/infrastructure.yaml is shared by boe"

  @DEV-146
  Scenario: Changing a shared file gives a new version to the pipelines that include it, and only to them
    Given "ana" created the system "boe" with the shared files "shared/infrastructure.yaml" and "shared/other.yaml"
    And "ana" deployed the pipeline "boe-crawler" of "boe", whose main file includes "shared/infrastructure.yaml"
    And "ana" deployed the pipeline "boe-mounter" of "boe", whose main file includes "shared/other.yaml"
    When "ana" changes the shared file "shared/infrastructure.yaml" of "boe"
    Then the API lists "boe-crawler" of "boe" in its version 2
    And the API lists "boe-mounter" of "boe" in its version 1

  @DEV-146
  Scenario: A system is listed with its pipelines, their node, version and state
    Given "ana" created the system "boe" with the shared file "shared/infrastructure.yaml"
    And "ana" deployed the pipeline "boe-crawler" of "boe", whose main file includes "shared/infrastructure.yaml"
    When a reader asks for the system "boe"
    Then it has the shared file "shared/infrastructure.yaml" and the pipeline "boe-crawler", owned by "ana"
    And "boe-crawler" is on a node, in its version 1, "STARTING"

  @DEV-146
  Scenario: The files of a pipeline are read back as they were sent
    Given "ana" created the system "boe" with the shared file "shared/infrastructure.yaml"
    And "ana" deployed the pipeline "boe-crawler" of "boe", whose main file includes "shared/infrastructure.yaml"
    When a reader asks for the pipeline "boe-crawler" of "boe"
    Then it gets its main file as it was sent, and that it uses the shared file "shared/infrastructure.yaml"

  @DEV-146
  Scenario: Withdrawing a system withdraws all its pipelines
    Given "ana" created the system "boe" with the shared file "shared/infrastructure.yaml"
    And "ana" deployed the pipeline "boe-crawler" of "boe", whose main file includes "shared/infrastructure.yaml"
    When "ana" withdraws the system "boe"
    Then the API answers 204
    And no node gets "boe-crawler"
    And a reader asking for the system "boe" gets 404

  @DEV-146
  Scenario: A name that cannot go in a route is refused
    When "ana" creates the system "Boe Data"
    Then the API answers 400 with an error that says "lower-case letters, digits and hyphens"
