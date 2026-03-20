package com.demo.common.exception;

public class ResourceNotFoundException extends RuntimeException {
    /**
     * Constructs the exception with a message of the form "{@code <resource> not found: <id>}".
     *
     * @param resource human-readable entity name (e.g. {@code "Task"})
     * @param id       the identifier that was not found
     */
    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " not found: " + id);
    }
}
