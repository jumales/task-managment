package com.demo.common.exception;

/**
 * Thrown when an entity cannot be deleted because it still has active (non-deleted) related entities.
 * Maps to HTTP 409 Conflict.
 */
public class RelatedEntityActiveException extends RuntimeException {

    public RelatedEntityActiveException(String entity, String relatedEntity) {
        super("Cannot delete " + entity + " because it still has active " + relatedEntity);
    }
}
