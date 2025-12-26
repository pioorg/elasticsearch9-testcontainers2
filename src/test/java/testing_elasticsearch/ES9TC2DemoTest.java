package testing_elasticsearch;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.io.IOException;

public class ES9TC2DemoTest {
    // please note that the official Docker image comes from the Elastic's repository
    static final String ELASTICSEARCH_IMAGE = "docker.elastic.co/elasticsearch/elasticsearch:9.2.4";

    static ElasticsearchContainer container = new ElasticsearchContainer(ELASTICSEARCH_IMAGE);

    @Test
    void newClientTest() throws IOException {
        container.start();
        try (var client = ElasticsearchClient.of(c ->
            c.host("https://" + container.getHttpHostAddress())
                // username and password are fine in tests
                // in production, please use API keys whenever possible
                .usernameAndPassword("elastic", ElasticsearchContainer.ELASTICSEARCH_DEFAULT_PASSWORD)
                // there is no need to turn off security
                .sslContext(container.createSslContextFromCa()))) {

            HealthResponse health = client.cluster().health();
            Assertions.assertEquals("docker-cluster", health.clusterName());
            Assertions.assertEquals(HealthStatus.Green, health.status());
        }
    }
}
