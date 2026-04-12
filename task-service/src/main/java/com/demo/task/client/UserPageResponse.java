package com.demo.task.client;

import com.demo.common.dto.UserDto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Feign response wrapper for the paginated {@code GET /api/v1/users} endpoint.
 * Unknown fields (pageable, sort, etc.) are ignored so the record stays minimal.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserPageResponse(List<UserDto> content, int totalPages, long totalElements, boolean last) {}
