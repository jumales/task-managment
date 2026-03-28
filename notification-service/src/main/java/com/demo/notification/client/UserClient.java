package com.demo.notification.client;

import com.demo.common.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/** Feign client for retrieving user details from user-service. */
@FeignClient(name = "user-service")
public interface UserClient {

    /** Fetches a user from the user-service by UUID. */
    @GetMapping("/api/v1/users/{id}")
    UserDto getUserById(@PathVariable UUID id);
}
