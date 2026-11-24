package software.spool.api.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

public class SystemSteps {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String INFRASTRUCTURE = """
            infrastructure:
              bus: {kind: EVENT_BUS, type: IN_MEMORY}
              inbox: {kind: INBOX, type: FILE_SYSTEM, configuration: {path: /data/boe/inbox}}
              lake: {kind: DATA_LAKE, type: FILE_SYSTEM, configuration: {path: /data/boe/lake}}
            """;

    private final ApiUnderTest api;
    private final Map<String, String> mainFiles = new HashMap<>();
    private final Map<String, Map<String, String>> sharedFiles = new HashMap<>();

    public SystemSteps(ApiUnderTest api) {
        this.api = api;
    }

    @Given("{string} created the system {string} with the shared file {string}")
    public void createdTheSystem(String person, String system, String shared) throws Exception {
        putSystem(system, Map.of(shared, INFRASTRUCTURE));
        assertThat(api.lastResponse().statusCode()).as(api.lastResponse().body()).isEqualTo(201);
    }

    @Given("{string} created the system {string} with the shared files {string} and {string}")
    public void createdTheSystemWithTwo(String person, String system, String first, String second) throws Exception {
        putSystem(system, Map.of(first, INFRASTRUCTURE, second, INFRASTRUCTURE.replace("/data/boe", "/data/boe-other")));
        assertThat(api.lastResponse().statusCode()).as(api.lastResponse().body()).isEqualTo(201);
    }

    @When("{string} creates the system {string}")
    public void createsTheSystem(String person, String system) throws Exception {
        api.send("PUT", "/spool/systems/" + system.replace(" ", "%20"), "{\"files\":{}}",
                Optional.of(ApiUnderTest.DEPLOYER_SECRET), "application/json");
    }

    @Given("{string} deployed the pipeline {string} of {string}, whose main file includes {string}")
    public void deployedThePipeline(String person, String pipeline, String system, String included) throws Exception {
        deploysThePipeline(person, pipeline, system, included);
        assertThat(api.lastResponse().statusCode()).as(api.lastResponse().body()).isEqualTo(201);
    }

    @When("{string} deploys the pipeline {string} of {string}, whose main file includes {string}")
    public void deploysThePipeline(String person, String pipeline, String system, String included) throws Exception {
        String main = pipeline + ".spool.yaml";
        String text = "include: [" + included + "]\nmodules:\n  - ingester: {type: REACTIVE, id: " + pipeline + "-ingester}\n";
        mainFiles.put(pipeline, text);
        putPipeline(system, pipeline, main, Map.of(main, text));
    }

    @When("{string} deploys the pipeline {string} of {string} with its own {string}")
    public void deploysWithItsOwn(String person, String pipeline, String system, String path) throws Exception {
        String main = pipeline + ".spool.yaml";
        putPipeline(system, pipeline, main, Map.of(main, "include: [" + path + "]\nmodules: []\n", path, INFRASTRUCTURE));
    }

    @When("{string} changes the shared file {string} of {string}")
    public void changesTheSharedFile(String person, String path, String system) throws Exception {
        Map<String, String> files = new HashMap<>(sharedFiles.get(system));
        files.put(path, INFRASTRUCTURE.replace("/data/boe", "/data/boe-changed"));
        putSystem(system, files);
        assertThat(api.lastResponse().statusCode()).as(api.lastResponse().body()).isEqualTo(200);
    }

    @When("{string} withdraws the system {string}")
    public void withdrawsTheSystem(String person, String system) throws Exception {
        api.send("DELETE", "/spool/systems/" + system, "", Optional.of(ApiUnderTest.DEPLOYER_SECRET));
    }

    @When("a reader asks for the system {string}")
    public void aReaderAsksForTheSystem(String system) throws Exception {
        api.send("GET", "/spool/systems/" + system, "", Optional.of(ApiUnderTest.READER_SECRET));
    }

    @When("a reader asks for the pipeline {string} of {string}")
    public void aReaderAsksForThePipeline(String pipeline, String system) throws Exception {
        api.send("GET", "/spool/systems/" + system + "/pipelines/" + pipeline, "", Optional.of(ApiUnderTest.READER_SECRET));
    }

    @Then("the node of {string} gets its main file and {string}")
    public void theNodeGetsItsFiles(String pipeline, String shared) throws Exception {
        JsonNode assignment = assignmentOf(pipeline);
        List<String> files = new ArrayList<>();
        assignment.get("files").fieldNames().forEachRemaining(files::add);
        assertThat(files).containsExactlyInAnyOrder(pipeline + ".spool.yaml", shared);
    }

    @Then("no node gets {string}")
    public void noNodeGets(String pipeline) throws Exception {
        assertThat(assignmentsOfEveryNode()).noneMatch(assignment -> assignment.get("name").asText().endsWith("." + pipeline));
    }

    @Then("a reader asking for the system {string} gets {int}")
    public void aReaderAskingGets(String system, int status) throws Exception {
        assertThat(read("/spool/systems/" + system).statusCode()).isEqualTo(status);
    }

    @Then("the API lists {string} of {string} in its version {int}")
    public void theApiListsInVersion(String pipeline, String system, int version) throws Exception {
        assertThat(pipelineIn(JSON.readTree(read("/spool/systems/" + system).body()), pipeline).get("version").asInt())
                .isEqualTo(version);
    }

    @Then("it has the shared file {string} and the pipeline {string}, owned by {string}")
    public void itHas(String shared, String pipeline, String owner) throws Exception {
        JsonNode system = JSON.readTree(api.lastResponse().body());
        assertThat(api.lastResponse().statusCode()).isEqualTo(200);
        assertThat(system.get("sharedFiles").has(shared)).isTrue();
        assertThat(system.get("owner").asText()).isEqualTo(owner);
        assertThat(pipelineIn(system, pipeline)).isNotNull();
    }

    @Then("{string} is on a node, in its version {int}, {string}")
    public void isOnANode(String pipeline, int version, String state) throws Exception {
        JsonNode listed = pipelineIn(JSON.readTree(api.lastResponse().body()), pipeline);
        assertThat(listed.get("node").isNull()).isFalse();
        assertThat(listed.get("version").asInt()).isEqualTo(version);
        assertThat(listed.get("state").asText()).isEqualTo(state);
    }

    @Then("it gets its main file as it was sent, and that it uses the shared file {string}")
    public void itGetsItsMainFile(String shared) throws Exception {
        JsonNode pipeline = JSON.readTree(api.lastResponse().body());
        assertThat(api.lastResponse().statusCode()).isEqualTo(200);
        String name = pipeline.get("name").asText();
        assertThat(pipeline.get("files").get(pipeline.get("main").asText()).asText()).isEqualTo(mainFiles.get(name));
        assertThat(pipeline.get("sharedFilesUsed").get(0).asText()).isEqualTo(shared);
    }

    private void putSystem(String system, Map<String, String> files) throws Exception {
        sharedFiles.put(system, files);
        api.send("PUT", "/spool/systems/" + system, JSON.writeValueAsString(Map.of("files", files)),
                Optional.of(ApiUnderTest.DEPLOYER_SECRET), "application/json");
    }

    private void putPipeline(String system, String pipeline, String main, Map<String, String> files) throws Exception {
        api.send("PUT", "/spool/systems/" + system + "/pipelines/" + pipeline,
                JSON.writeValueAsString(Map.of("main", main, "files", files)),
                Optional.of(ApiUnderTest.DEPLOYER_SECRET), "application/json");
    }

    private HttpResponse<String> read(String path) throws Exception {
        return api.query("GET", path, "", Optional.of(ApiUnderTest.READER_SECRET));
    }

    private JsonNode assignmentOf(String pipeline) throws Exception {
        return assignmentsOfEveryNode().stream().filter(assignment -> assignment.get("name").asText().endsWith("." + pipeline))
                .findFirst().orElseThrow(() -> new AssertionError("no node gets " + pipeline));
    }

    private List<JsonNode> assignmentsOfEveryNode() throws Exception {
        List<JsonNode> all = new ArrayList<>();
        for (String node : List.of("node-a", "node-b")) {
            HttpResponse<String> response = api.query("GET", "/spool/cluster/nodes/" + node + "/assignments", "",
                    Optional.of(ApiUnderTest.NODE_SECRET));
            JSON.readTree(response.body()).get("deployments").forEach(all::add);
        }
        return all;
    }

    private static JsonNode pipelineIn(JsonNode system, String pipeline) {
        return StreamSupport.stream(system.get("pipelines").spliterator(), false)
                .filter(listed -> listed.get("name").asText().equals(pipeline)).findFirst()
                .orElseThrow(() -> new AssertionError(pipeline + " is not listed"));
    }
}
