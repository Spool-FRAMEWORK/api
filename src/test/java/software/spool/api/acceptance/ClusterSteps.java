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

public class ClusterSteps {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ApiUnderTest api;

    public ClusterSteps(ApiUnderTest api) {
        this.api = api;
    }

    @Given("the Spool API with a heartbeat timeout of {int} seconds")
    public void theApi(int seconds) throws Exception {
        api.start(Duration.ofSeconds(seconds));
    }

    @When("the node {string} registers with the address {string}")
    public void registers(String id, String address) throws Exception {
        HttpResponse<String> response = api.send("POST", "/spool/cluster/nodes",
                "{\"id\":\"" + id + "\",\"address\":\"" + address + "\"}", Optional.of(ApiUnderTest.NODE_SECRET));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    }

    @When("{int} seconds pass")
    public void secondsPass(int seconds) {
        api.advance(Duration.ofSeconds(seconds));
    }

    @When("{string} sends a heartbeat")
    public void sendsAHeartbeat(String id) throws Exception {
        api.send("PUT", "/spool/cluster/nodes/" + id + "/heartbeat", "", Optional.of(ApiUnderTest.NODE_SECRET));
    }

    @When("a caller with no credentials asks for the list of nodes")
    public void noCredentials() throws Exception {
        api.send("GET", "/spool/cluster/nodes", "", Optional.empty());
    }

    @When("a caller with a wrong signature asks for the list of nodes")
    public void wrongSignature() throws Exception {
        api.send("GET", "/spool/cluster/nodes", "", Optional.of("not-the-secret"));
    }

    @When("a node, which cannot read asks for the list of nodes")
    public void aNodeAsks() throws Exception {
        api.send("GET", "/spool/cluster/nodes", "", Optional.of(ApiUnderTest.NODE_SECRET));
    }

    @When("a caller with no credentials asks for {string}")
    public void noCredentialsAskFor(String path) throws Exception {
        api.send("GET", path, "", Optional.empty());
    }

    @Then("the API lists {string} as up at {string}")
    public void listedAsUp(String id, String address) throws Exception {
        assertThat(nodesNamed(id)).singleElement().satisfies(node -> {
            assertThat(node.get("state").asText()).isEqualTo("UP");
            assertThat(node.get("address").asText()).isEqualTo(address);
        });
    }

    @Then("the API lists only one {string}, up at {string}")
    public void listedOnce(String id, String address) throws Exception {
        listedAsUp(id, address);
    }

    @Then("the API lists {string} as down")
    public void listedAsDown(String id) throws Exception {
        assertThat(nodesNamed(id)).singleElement().satisfies(node -> assertThat(node.get("state").asText()).isEqualTo("DOWN"));
    }

    @Then("the API answers {int} with an error that says {string}")
    public void answersWithError(int status, String text) throws Exception {
        HttpResponse<String> response = api.lastResponse();
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(JSON.readTree(response.body()).get("error").asText()).contains(text);
    }

    @Then("the API answers {int}")
    public void answers(int status) {
        HttpResponse<String> response = api.lastResponse();
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
    }

    @Then("the API answers {int} with the OpenAPI contract")
    public void answersTheContract(int status) {
        HttpResponse<String> response = api.lastResponse();
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.body()).startsWith("openapi: 3.1.0").contains("/spool/cluster/nodes");
    }

    private List<JsonNode> nodesNamed(String id) throws Exception {
        HttpResponse<String> list = api.query("GET", "/spool/cluster/nodes", "", Optional.of(ApiUnderTest.READER_SECRET));
        assertThat(list.statusCode()).as(list.body()).isEqualTo(200);
        return StreamSupport.stream(JSON.readTree(list.body()).get("nodes").spliterator(), false)
                .filter(node -> node.get("id").asText().equals(id)).toList();
    }
}
