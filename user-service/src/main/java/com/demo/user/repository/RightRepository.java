package com.demo.user.repository;

import com.demo.user.model.Right;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RightRepository extends JpaRepository<Right, UUID> {
    /** Returns the right with the given name, if one exists. */
    Optional<Right> findByName(String name);
}
