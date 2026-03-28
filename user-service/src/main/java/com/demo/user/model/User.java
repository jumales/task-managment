package com.demo.user.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;
    private String email;

    /** Unique login handle; required on creation and immutable thereafter. */
    @Column(nullable = false)
    private String username;

    /** Whether the account is active. Defaults to true on creation. */
    private boolean active;

    /** UUID of the file-service record for the user's profile picture; null if not set. */
    private UUID avatarFileId;

    /** ISO 639-1 language code for the user's preferred UI language (e.g. "en", "hr"). Defaults to "en". */
    @Column(nullable = false)
    @Builder.Default
    private String language = "en";

    private Instant deletedAt;
}
