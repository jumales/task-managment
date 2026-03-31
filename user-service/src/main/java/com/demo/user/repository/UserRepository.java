package com.demo.user.repository;

import com.demo.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Returns true if a non-deleted user with the given username already exists. */
    boolean existsByUsername(String username);

    /** Returns the active user with the given username, or empty if not found. */
    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

}
