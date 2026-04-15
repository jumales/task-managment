package com.demo.reporting.repository;

import java.util.UUID;

/**
 * JPQL projection for the "open tasks per project" query.
 * Returned by {@link ReportTaskRepository#countOpenByProject}.
 */
public interface ProjectTaskCountProjection {

    /** The project id. */
    UUID getProjectId();

    /** The project display name. */
    String getProjectName();

    /** Total number of open tasks in the project (all users). */
    Long getTotalOpenCount();

    /** Number of open tasks in the project assigned to the requesting user. */
    Long getMyOpenCount();
}
