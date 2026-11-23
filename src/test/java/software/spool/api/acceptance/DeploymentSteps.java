package software.spool.api.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

public class DeploymentSteps {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DESCRIPTOR = """
            infrastructure:
              bus: {kind: EVENT_BUS, type: IN_MEMORY}
              inbox: {kind: INBOX, type: FILE_SYSTEM, configuration: {path: /data/%s/inbox}}
              lake: {kind: DATA_LAKE, type: FILE_SYSTEM, configuration: {path: /data/%s/lake}}
            modules:
              - ingester: {type: REACTIVE, id: %s-ingester}
            """;

    private final ApiUnderTest api;

    public DeploymentSteps(ApiUnderTest api) {
        this.api = api;
    }

    @Given("{string} can deploy")
    public void canDeploy(String person) {
        assertThat(person).isEqualTo(ApiUnderTest.DEPLOYER);
    }

    @Given("the nodes {string} and {string} are up")
    public void theNodesAreUp(String first, String second) throws Exception {
        register(first);
        register(second);
    }

    @Given("{string} already runs the deployment {string}")
    public void alreadyRuns(String node, String deployment) throws Exception {
        deploy(deployment, descriptorOf(deployment));
        theApiListsOn(deployment, node);
    }

    @Given("{string} deployed the descriptor {string} on {string}")
    public void deployedOn(String person, String deployment, String node) throws Exception {
        deploy(deployment, descriptorOf(deployment));
        theApiListsOn(deployment, node);
    }

    @When("{string} deploys the descriptor {string}")
    public void deploys(String person, String deployment) throws Exception {
        deploy(deployment, descriptorOf(deployment));
    }

    @When("{string} deploys a new version of the descriptor {string}")
    public void deploysANewVersion(String person, String deployment) throws Exception {
        deploy(deployment, descriptorOf(deployment).replace("id: " + deployment + "-ingester",
                "id: " + deployment + "-ingester-2"));
    }

    @When("{string} deploys a descriptor whose piece {string} has no kind")
    public void deploysAPieceWithoutKind(String person, String piece) throws Exception {
        deploy("orders", descriptorOf("orders").replace(piece + ": {kind: DATA_LAKE, ", piece + ": {"));
    }

    @When("{string} withdraws the deployment {string}")
    public void withdraws(String person, String deployment) throws Exception {
        api.send("DELETE", "/spool/deployments/" + deployment, "", Optional.of(ApiUnderTest.DEPLOYER_SECRET));
    }

    @When("a caller with no credentials deploys the descriptor {string}")
    public void noCredentialsDeploy(String deployment) throws Exception {
        api.send("PUT", "/spool/deployments/" + deployment, descriptorOf(deployment), Optional.empty());
    }

    @When("a node, which cannot deploy deploys the descriptor {string}")
    public void aNodeDeploys(String deployment) throws Exception {
        api.send("PUT", "/spool/deployments/" + deployment, descriptorOf(deployment), Optional.of(ApiUnderTest.NODE_SECRET));
    }

    @When("{int} seconds pass, with only {string} sending heartbeats")
    public void secondsPassWithOnlyOneBeating(int seconds, String node) throws Exception {
        api.advance(Duration.ofSeconds(seconds - 1L));
        api.query("PUT", "/spool/cluster/nodes/" + node + "/heartbeat", "", Optional.of(ApiUnderTest.NODE_SECRET));
        api.advance(Duration.ofSeconds(1));
    }

    @Then("the API answers {int} with the deployment {string}")
    public void answersWithTheDeployment(int status, String deployment) throws Exception {
        HttpResponse<String> response = api.lastResponse();
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(JSON.readTree(response.body()).get("name").asText()).isEqualTo(deployment);
    }

    @Then("the API lists {string} on {string}, owned by {string}")
    public void theApiListsOnOwnedBy(String deployment, String node, String owner) throws Exception {
        JsonNode listed = listed(deployment);
        assertThat(listed.get("node").asText()).isEqualTo(node);
        assertThat(listed.get("owner").asText()).isEqualTo(owner);
    }

    @Then("the API lists {string} on {string}")
    public void theApiListsOn(String deployment, String node) throws Exception {
        assertThat(listed(deployment).get("node").asText()).isEqualTo(node);
    }

    @Then("the API lists {string} on {string} in its version {int}")
    public void theApiListsOnInVersion(String deployment, String node, int version) throws Exception {
        JsonNode listed = listed(deployment);
        assertThat(listed.get("node").asText()).isEqualTo(node);
        assertThat(listed.get("version").asInt()).isEqualTo(version);
    }

    @Then("the API lists {string} as waiting for a node")
    public void theApiListsAsWaiting(String deployment) throws Exception {
        JsonNode listed = listed(deployment);
        assertThat(listed.get("state").asText()).isEqualTo("WAITING");
        assertThat(listed.get("node").isNull()).isTrue();
    }

    @Then("the API lists no deployments")
    public void theApiListsNoDeployments() throws Exception {
        assertThat(deployments()).isEmpty();
    }

    private void register(String node) throws Exception {
        HttpResponse<String> response = api.query("POST", "/spool/cluster/nodes",
                "{\"id\":\"" + node + "\",\"address\":\"http://" + node + ":8080\"}", Optional.of(ApiUnderTest.NODE_SECRET));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    }

    private void deploy(String deployment, String descriptor) throws Exception {
        api.send("PUT", "/spool/deployments/" + deployment, descriptor, Optional.of(ApiUnderTest.DEPLOYER_SECRET));
    }

    private JsonNode listed(String deployment) throws Exception {
        List<JsonNode> named = deployments().stream().filter(listed -> listed.get("name").asText().equals(deployment)).toList();
        assertThat(named).as("deployments named " + deployment).hasSize(1);
        return named.get(0);
    }

    private List<JsonNode> deployments() throws Exception {
        HttpResponse<String> list = api.query("GET", "/spool/deployments", "", Optional.of(ApiUnderTest.READER_SECRET));
        assertThat(list.statusCode()).as(list.body()).isEqualTo(200);
        return StreamSupport.stream(JSON.readTree(list.body()).get("deployments").spliterator(), false).toList();
    }

    private static String descriptorOf(String deployment) {
        return DESCRIPTOR.formatted(deployment, deployment, deployment);
    }
}
