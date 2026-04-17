package com.demo.search.config;

import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Bootstraps an Elasticsearch ILM policy and index template for Logstash log indices on startup.
 * Both PUT operations are idempotent upserts — safe to run on every restart.
 * Targets only {@code logstash-*} indices; task search indices are unaffected.
 */
@Component
public class ElasticsearchIlmConfig implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIlmConfig.class);

    private static final String ILM_POLICY_NAME = "logstash-ttl-policy";
    private static final String INDEX_TEMPLATE_NAME = "logstash-ttl-template";

    @Value("${ttl.elasticsearch.log-retention-days:30}")
    private int logRetentionDays;

    private final RestClient restClient;

    public ElasticsearchIlmConfig(RestClient restClient) {
        this.restClient = restClient;
    }

    /** Upserts the ILM policy and the index template that links logstash-* indices to it. */
    @Override
    public void run(ApplicationArguments args) {
        applyIlmPolicy();
        applyIndexTemplate();
    }

    private void applyIlmPolicy() {
        String body = """
                {
                  "policy": {
                    "phases": {
                      "hot":    { "min_age": "0ms", "actions": {} },
                      "delete": { "min_age": "%dd", "actions": { "delete": {} } }
                    }
                  }
                }
                """.formatted(logRetentionDays);
        try {
            Request request = new Request("PUT", "/_ilm/policy/" + ILM_POLICY_NAME);
            request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
            restClient.performRequest(request);
            log.info("ILM policy '{}' applied (retention: {} days)", ILM_POLICY_NAME, logRetentionDays);
        } catch (Exception ex) {
            log.warn("Failed to apply ILM policy '{}': {}", ILM_POLICY_NAME, ex.getMessage());
        }
    }

    private void applyIndexTemplate() {
        String body = """
                {
                  "index_patterns": ["logstash-*"],
                  "template": {
                    "settings": {
                      "index.lifecycle.name": "%s"
                    }
                  }
                }
                """.formatted(ILM_POLICY_NAME);
        try {
            Request request = new Request("PUT", "/_index_template/" + INDEX_TEMPLATE_NAME);
            request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
            restClient.performRequest(request);
            log.info("Index template '{}' applied for logstash-* indices", INDEX_TEMPLATE_NAME);
        } catch (Exception ex) {
            log.warn("Failed to apply index template '{}': {}", INDEX_TEMPLATE_NAME, ex.getMessage());
        }
    }
}
