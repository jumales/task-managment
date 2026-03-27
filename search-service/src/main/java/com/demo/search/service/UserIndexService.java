package com.demo.search.service;

import com.demo.common.event.UserEvent;
import com.demo.search.document.UserDocument;
import com.demo.search.repository.UserSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Manages the Elasticsearch user index: indexes user documents on CREATED/UPDATED events
 * and removes them on DELETED events.
 */
@Service
public class UserIndexService {

    private static final Logger log = LoggerFactory.getLogger(UserIndexService.class);

    private final UserSearchRepository repository;

    public UserIndexService(UserSearchRepository repository) {
        this.repository = repository;
    }

    /** Indexes or re-indexes a user document from a CREATED or UPDATED event. */
    public void index(UserEvent event) {
        UserDocument doc = UserDocument.builder()
                .id(event.getUserId().toString())
                .name(event.getName())
                .email(event.getEmail())
                .username(event.getUsername())
                .active(event.isActive())
                .build();
        repository.save(doc);
        log.info("Indexed user {} ({})", event.getUserId(), event.getEventType());
    }

    /** Removes a user document from the index on a DELETED event. */
    public void delete(UserEvent event) {
        repository.deleteById(event.getUserId().toString());
        log.info("Removed user {} from index", event.getUserId());
    }

    /**
     * Searches users by full-text query across name, email, and username.
     *
     * @param query free-text search string
     * @return matching user documents
     */
    public List<UserDocument> search(String query) {
        return repository.search(query);
    }
}
