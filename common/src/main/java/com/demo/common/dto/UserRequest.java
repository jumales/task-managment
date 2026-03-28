package com.demo.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserRequest {
    private String name;

    @Email(message = "must be a valid email address")
    private String email;

    @NotBlank(message = "username is required")
    private String username;
    private boolean active = true;
}
