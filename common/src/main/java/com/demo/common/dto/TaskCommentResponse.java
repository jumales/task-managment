package com.demo.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class TaskCommentResponse {
    private UUID id;
    private String content;
    private Instant createdAt;
}
