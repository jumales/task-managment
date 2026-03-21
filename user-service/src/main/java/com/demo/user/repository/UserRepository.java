package com.demo.user.repository;

import com.demo.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Returns true if a non-deleted user with the given username already exists. */
    boolean existsByUsername(String username);

    /** Returns true if a non-deleted user other than {@code id} already holds the given username. */
    boolean existsByUsernameAndIdNot(String username, UUID id);
}
