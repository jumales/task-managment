package com.demo.common.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private UUID id;
    private String name;
    private String email;
    private String username;
    private boolean active;
    /** UUID of the file-service record for the user's profile picture; null if not set. */
    private UUID avatarFileId;

    /** ISO 639-1 language code for the user's preferred UI language (e.g. "en", "hr"). */
    private String language;

    /** Manageable Keycloak realm roles held by this user (excludes WEB_APP). Empty for list endpoints. */
    private List<String> roles;
}
