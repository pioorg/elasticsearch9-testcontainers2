package testing_elasticsearch;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.elasticsearch.KibanaContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;

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

}
