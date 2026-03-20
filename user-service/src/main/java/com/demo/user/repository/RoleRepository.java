package com.demo.user.repository;

import com.demo.user.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    /** Returns the role with the given name, if one exists. */
    Optional<Role> findByName(String name);
}
