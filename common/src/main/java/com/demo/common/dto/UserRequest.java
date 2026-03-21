package com.demo.common.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class UserRequest {
    private String name;

    @Email(message = "must be a valid email address")
    private String email;

    private String username;
    private boolean active = true;
}
