package com.demo.common.dto;

import lombok.Data;

@Data
public class UserRequest {
    private String name;
    private String email;
    private String username;
    private boolean active = true;
}
