package com.demo.search.client;

import com.demo.common.dto.PageResponse;
import com.demo.common.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Feign client for fetching all users from user-service during re-indexing. */
@FeignClient(name = "user-service")
public interface UserServiceClient {

    @GetMapping("/api/v1/users")
    PageResponse<UserDto> getAll(@RequestParam int page, @RequestParam int size);
}
