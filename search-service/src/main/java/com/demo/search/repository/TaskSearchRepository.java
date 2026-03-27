package com.demo.search.repository;

import com.demo.search.document.TaskDocument;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

/**
 * Spring Data Elasticsearch repository for task documents.
 * Supports full-text search across title and description using a multi_match query.
 */
public interface TaskSearchRepository extends ElasticsearchRepository<TaskDocument, String> {

    /**
     * Full-text search across title and description using Elasticsearch multi_match.
     * Uses {@code best_fields} with AUTO fuzziness to handle typos.
     */
    @Query("{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"title\", \"description\"], \"type\": \"best_fields\", \"fuzziness\": \"AUTO\"}}")
    List<TaskDocument> search(String query);
}
