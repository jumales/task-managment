package com.demo.search.repository;

import com.demo.search.document.UserDocument;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

/**
 * Spring Data Elasticsearch repository for user documents.
 * Supports full-text search across name, email, and username using a multi_match query.
 */
public interface UserSearchRepository extends ElasticsearchRepository<UserDocument, String> {

    /**
     * Full-text search across name, email, and username using Elasticsearch multi_match.
     * Uses {@code best_fields} with AUTO fuzziness to handle typos.
     */
    @Query("{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name\", \"email\", \"username\"], \"type\": \"best_fields\", \"fuzziness\": \"AUTO\"}}")
    List<UserDocument> search(String query);
}
