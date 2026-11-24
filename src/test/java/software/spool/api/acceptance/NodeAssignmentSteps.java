package software.spool.api.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

public class NodeAssignmentSteps {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ApiUnderTest api;

    public NodeAssignmentSteps(ApiUnderTest api) {
        this.api = api;
    }

    @When("{string} asks for its assignments")
    public void asksForItsAssignments(String node) throws Exception {
        api.send("GET", "/spool/cluster/nodes/" + node + "/assignments", "", Optional.of(ApiUnderTest.NODE_SECRET));
    }

    @When("a reader asks for the assignments of {string}")
    public void aReaderAsks(String node) throws Exception {
        api.send("GET", "/spool/cluster/nodes/" + node + "/assignments", "", Optional.of(ApiUnderTest.READER_SECRET));
    }

    @When("{string} says {string} in its version {int} runs")
    public void saysItRuns(String node, String deployment, int version) throws Exception {
        report(node, deployment, "{\"version\":" + version + ",\"state\":\"RUNNING\"}");
    }

    @Given("{string} said {string} in its version {int} failed with {string}")
    public void saidItFailed(String node, String deployment, int version, String error) throws Exception {
        saysItFailed(node, deployment, version, error);
        assertThat(api.lastResponse().statusCode()).as(api.lastResponse().body()).isEqualTo(204);
    }

    @When("{string} says {string} in its version {int} failed with {string}")
    public void saysItFailed(String node, String deployment, int version, String error) throws Exception {
        report(node, deployment, JSON.writeValueAsString(new Failure(version, "FAILED", error)));
    }

    @Then("it gets the deployment {string} in its version {int}, with its descriptor")
    public void itGetsTheDeployment(String deployment, int version) throws Exception {
        assertThat(assigned()).singleElement().satisfies(assignment -> {
            assertThat(assignment.get("name").asText()).isEqualTo(deployment);
            assertThat(assignment.get("version").asInt()).isEqualTo(version);
            assertThat(assignment.get("files").get(assignment.get("main").asText()).asText())
                    .contains("id: " + deployment + "-ingester");
        });
    }

    @Then("{string} gets the deployment {string} with the files {string}")
    public void getsTheDeploymentWithTheFile(String node, String deployment, String file) throws Exception {
        assertThat(filesOf(node, deployment)).containsExactly(file);
    }

    @Then("{string} gets the deployment {string} with the files {string} and {string}")
    public void getsTheDeploymentWithTheFiles(String node, String deployment, String main, String other) throws Exception {
        assertThat(filesOf(node, deployment)).containsExactlyInAnyOrder(main, other);
    }

    @Then("it gets no deployments")
    public void itGetsNoDeployments() throws Exception {
        assertThat(assigned()).isEmpty();
    }

    @Then("it may keep running them for {int} seconds without the API")
    public void itMayKeepRunningThem(int seconds) throws Exception {
        assertThat(JSON.readTree(api.lastResponse().body()).get("leaseSeconds").asInt()).isEqualTo(seconds);
    }

    private List<String> filesOf(String node, String deployment) throws Exception {
        HttpResponse<String> response = api.query("GET", "/spool/cluster/nodes/" + node + "/assignments", "",
                Optional.of(ApiUnderTest.NODE_SECRET));
        JsonNode assignment = StreamSupport.stream(JSON.readTree(response.body()).get("deployments").spliterator(), false)
                .filter(candidate -> candidate.get("name").asText().equals(deployment)).findFirst().orElseThrow();
        List<String> files = new ArrayList<>();
        assignment.get("files").fieldNames().forEachRemaining(files::add);
        return files;
    }

    private void report(String node, String deployment, String body) throws Exception {
        api.send("PUT", "/spool/cluster/nodes/" + node + "/assignments/" + deployment, body,
                Optional.of(ApiUnderTest.NODE_SECRET));
    }

    private List<JsonNode> assigned() throws Exception {
        HttpResponse<String> response = api.lastResponse();
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return StreamSupport.stream(JSON.readTree(response.body()).get("deployments").spliterator(), false).toList();
    }

    private record Failure(int version, String state, String error) {
    }
}
