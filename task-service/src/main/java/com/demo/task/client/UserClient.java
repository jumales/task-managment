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

    /**
     * Fetches multiple users from the user-service in a single HTTP call.
     * Returns only users that were found; silently omits unknown IDs.
     */
    @GetMapping("/api/v1/users/batch")
    List<UserDto> getUsersByIds(@RequestParam("ids") List<UUID> ids);
}
