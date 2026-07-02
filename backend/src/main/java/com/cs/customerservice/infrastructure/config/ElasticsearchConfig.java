package com.cs.customerservice.infrastructure.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(value = "cs.knowledge.es.enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchConfig {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchConfig.class);

    @Value("${cs.knowledge.es.host:localhost}")
    private String host;

    @Value("${cs.knowledge.es.port:9200}")
    private int port;

    @Value("${cs.knowledge.es.protocol:http}")
    private String protocol;

    @Bean
    @ConditionalOnProperty(value = "cs.knowledge.es.enabled", havingValue = "true", matchIfMissing = true)
    public ElasticsearchClient elasticsearchClient() {
        log.info("Connecting to Elasticsearch at {}://{}:{}", protocol, host, port);
        RestClient restClient = RestClient.builder(
                new HttpHost(host, port, protocol)
        ).build();
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
