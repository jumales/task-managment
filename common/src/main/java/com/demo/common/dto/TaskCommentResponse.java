package com.demo.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a task comment, including the author's identity and display name.
 * {@code userId} and {@code userName} are null for legacy comments created before author tracking was introduced.
 */
@Getter
@AllArgsConstructor
public class TaskCommentResponse {
    private UUID id;
    /** UUID of the user who posted this comment; null for legacy comments. */
    private UUID userId;
    /** Display name of the user who posted this comment; null when userId is null or user-service unavailable. */
    private String userName;
    private String content;
    private Instant createdAt;
}
