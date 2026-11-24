package software.spool.api.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

public class NodeLabelSteps {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ApiUnderTest api;

    public NodeLabelSteps(ApiUnderTest api) {
        this.api = api;
    }

    @When("the node {string} registers with the address {string} and the labels {string}")
    public void registersWithLabels(String id, String address, String labels) throws Exception {
        HttpResponse<String> response = api.send("POST", "/spool/cluster/nodes",
                JSON.writeValueAsString(Map.of("id", id, "address", address, "labels", listOf(labels))),
                Optional.of(ApiUnderTest.NODE_SECRET));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    }

    @Given("the node {string} is up with the labels {string}")
    public void isUpWithLabels(String id, String labels) throws Exception {
        registersWithLabels(id, "http://" + id + ":8080", labels);
    }

    @When("{string} deploys the descriptor {string} asking for the labels {string}")
    @Given("{string} deployed the descriptor {string} asking for the labels {string}")
    public void deploysAskingFor(String person, String deployment, String labels) throws Exception {
        deploy(deployment, DeploymentSteps.descriptorOf(deployment), labels);
    }

    @Given("{string} deployed the descriptor {string} asking for the labels {string} on {string}")
    public void deployedAskingForOn(String person, String deployment, String labels, String node) throws Exception {
        deploy(deployment, DeploymentSteps.descriptorOf(deployment), labels);
        assertThat(listed(deployment).get("node").asText()).isEqualTo(node);
    }

    @When("{string} deploys a new version of the descriptor {string} asking for the labels {string}")
    public void deploysANewVersionAskingFor(String person, String deployment, String labels) throws Exception {
        deploy(deployment, DeploymentSteps.descriptorOf(deployment)
                .replace("id: " + deployment + "-ingester", "id: " + deployment + "-ingester-2"), labels);
    }

    @When("{int} seconds pass, with only {string} and {string} sending heartbeats")
    public void secondsPassWithOnlyTwoBeating(int seconds, String first, String second) throws Exception {
        api.advance(Duration.ofSeconds(seconds - 1L));
        for (String node : List.of(first, second)) {
            api.query("PUT", "/spool/cluster/nodes/" + node + "/heartbeat", "", Optional.of(ApiUnderTest.NODE_SECRET));
        }
        api.advance(Duration.ofSeconds(1));
    }

    @Then("the API lists the node {string} with the labels {string}")
    public void theApiListsTheNodeWithTheLabels(String id, String labels) throws Exception {
        HttpResponse<String> list = api.query("GET", "/spool/cluster/nodes", "", Optional.of(ApiUnderTest.READER_SECRET));
        assertThat(list.statusCode()).as(list.body()).isEqualTo(200);
        assertThat(StreamSupport.stream(JSON.readTree(list.body()).get("nodes").spliterator(), false)
                .filter(node -> node.get("id").asText().equals(id)))
                .singleElement()
                .satisfies(node -> assertThat(textsOf(node.get("labels"))).containsExactlyElementsOf(listOf(labels)));
    }

    @Then("the API lists {string} asking for the labels {string}")
    public void theApiListsAskingFor(String deployment, String labels) throws Exception {
        assertThat(textsOf(listed(deployment).get("labels"))).containsExactlyElementsOf(listOf(labels));
    }

    @Then("the API lists {string} as waiting for a node with the labels {string}")
    public void theApiListsAsWaitingForTheLabels(String deployment, String labels) throws Exception {
        JsonNode listed = listed(deployment);
        assertThat(listed.get("state").asText()).isEqualTo("WAITING");
        assertThat(listed.get("node").isNull()).isTrue();
        assertThat(textsOf(listed.get("labels"))).containsExactlyElementsOf(listOf(labels));
    }

    private void deploy(String deployment, String descriptor, String labels) throws Exception {
        String main = deployment + ".spool.yaml";
        api.send("PUT", "/spool/deployments/" + deployment,
                JSON.writeValueAsString(Map.of("main", main, "files", Map.of(main, descriptor), "labels", listOf(labels))),
                Optional.of(ApiUnderTest.DEPLOYER_SECRET), "application/json");
    }

    private JsonNode listed(String deployment) throws Exception {
        HttpResponse<String> list = api.query("GET", "/spool/deployments", "", Optional.of(ApiUnderTest.READER_SECRET));
        assertThat(list.statusCode()).as(list.body()).isEqualTo(200);
        List<JsonNode> named = StreamSupport.stream(JSON.readTree(list.body()).get("deployments").spliterator(), false)
                .filter(listed -> listed.get("name").asText().equals(deployment))
                .toList();
        assertThat(named).as("deployments named " + deployment).hasSize(1);
        return named.get(0);
    }

    private static List<String> listOf(String labels) {
        return Arrays.stream(labels.split(",")).map(String::trim).toList();
    }

    private static List<String> textsOf(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }
}
