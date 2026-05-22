package testing_elasticsearch;

import module java.base;
import module java.net.http;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.elasticsearch.KibanaContainer;

class KB9TC2DemoTest {

    @Test
    void startsKibanaNextToElasticsearch() throws Exception {
        try (
            ElasticsearchContainer elasticsearchContainer = new ElasticsearchContainer(
                "docker.elastic.co/elasticsearch/elasticsearch:9.4.1"
            );
            KibanaContainer kibanaContainer = new KibanaContainer(elasticsearchContainer);
            HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
        ) {
            kibanaContainer.start();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + kibanaContainer.getHttpHostAddress()))
                .GET()
                .build();

            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

            Assertions.assertTrue(Set.of(200, 302).contains(response.statusCode()));
        }
    }

    // for more, follow the docs: https://www.elastic.co/docs/explore-analyze/ai-features/agent-builder/kibana-api
    @Test
    void createsCustomToolAndVerifiesItIsListed() throws Exception {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Tool(String id, String type, String description) {}


        @JsonIgnoreProperties(ignoreUnknown = true)
        record ToolsPage(List<Tool> results) {}

        try (
            ElasticsearchContainer elasticsearchContainer = new ElasticsearchContainer(
                "docker.elastic.co/elasticsearch/elasticsearch:9.4.1"
            )
                // Agent Builder requires an appropriate license; use trial for this throwaway test cluster.
                .withEnv("xpack.license.self_generated.type", "trial");
            KibanaContainer kibanaContainer = new KibanaContainer(elasticsearchContainer);
            HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
        ) {
            kibanaContainer.start();

            ObjectMapper mapper = new ObjectMapper();

            String requestBody = """
                {
                    "id": "orders-by-state-by-week",
                    "type": "esql",
                    "description": "Counts orders grouped by state and week, within a given date range",
                    "tags": ["orders", "analytics"],
                    "configuration": {
                        "query": "FROM orders* | WHERE @timestamp >= ?startTime AND @timestamp <= ?endTime | STATS count = COUNT(*) BY state, BUCKET(@timestamp, 1 week)",
                        "params": {
                            "startTime": {
                                "type": "date",
                                "description": "Start of the date range (inclusive)",
                                "optional": false
                            },
                            "endTime": {
                                "type": "date",
                                "description": "End of the date range (inclusive)",
                                "optional": false
                            }
                        }
                    }
                }
                """;

            String basicAuth = Base64.getEncoder().encodeToString(
                ("elastic:" + ElasticsearchContainer.ELASTICSEARCH_DEFAULT_PASSWORD)
                    .getBytes(StandardCharsets.UTF_8)
            );
            HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://" + kibanaContainer.getHttpHostAddress() + "/api/agent_builder/tools"))
                .header("Authorization", "Basic " + basicAuth)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("kbn-xsrf", "true")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString());
            Assertions.assertEquals(200, createResponse.statusCode(),
                "Create tool failed. Response: " + createResponse.body());
            Tool created = mapper.readValue(createResponse.body(), Tool.class);
            Assertions.assertEquals("orders-by-state-by-week", created.id());
            Assertions.assertEquals("esql", created.type());

            HttpRequest listRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://" + kibanaContainer.getHttpHostAddress() + "/api/agent_builder/tools"))
                .header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString("elastic:changeme".getBytes(StandardCharsets.UTF_8)))
                .header("Accept", "application/json")
                .GET()
                .build();

            HttpResponse<String> listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
            Assertions.assertEquals(200, listResponse.statusCode(),
                "List tools failed. Response: " + listResponse.body());
            ToolsPage page = mapper.readValue(listResponse.body(), ToolsPage.class);
            Assertions.assertTrue(
                page.results().stream().anyMatch(t -> "orders-by-state-by-week".equals(t.id())),
                "Created tool should appear in the tools list"
            );
        }
    }

}
