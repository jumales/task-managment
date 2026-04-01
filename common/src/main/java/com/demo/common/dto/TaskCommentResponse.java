package com.demo.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

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
