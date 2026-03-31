package com.demo.task.client;

import com.demo.common.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "user-service")
public interface UserClient {

    /** Fetches a user from the user-service by UUID. */
    @GetMapping("/api/v1/users/{id}")
    UserDto getUserById(@PathVariable UUID id);

    /** Batch-fetches users by their IDs in a single HTTP call. */
    @GetMapping("/api/v1/users/batch")
    List<UserDto> getUsersByIds(@RequestParam("ids") List<UUID> ids);

    /** Looks up a user by their username; used to resolve the caller's user-service UUID from a JWT preferred_username claim. */
    @GetMapping("/api/v1/users/by-username")
    UserDto getUserByUsername(@RequestParam("username") String username);
}
