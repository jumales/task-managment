package com.demo.user.repository;

import com.demo.user.model.Role;
import com.demo.user.model.User;
import com.demo.user.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
    /** Returns all active role assignments for the given user. */
    List<UserRole> findByUser(User user);
    /** Returns the active role assignment for the given user and role, if one exists. */
    Optional<UserRole> findByUserAndRole(User user, Role role);
    /** Returns {@code true} if the user has at least one active role assignment. */
    boolean existsByUser(User user);
    /** Returns {@code true} if the role is assigned to at least one active user. */
    boolean existsByRole(Role role);
    /** Returns {@code true} if the specified role is actively assigned to the specified user. */
    boolean existsByUserAndRole(User user, Role role);

    /** Soft-deletes the role assignment between the given user and role. */
    @Modifying
    @Query("UPDATE UserRole ur SET ur.deletedAt = CURRENT_TIMESTAMP WHERE ur.user = :user AND ur.role = :role AND ur.deletedAt IS NULL")
    void softDeleteByUserAndRole(@Param("user") User user, @Param("role") Role role);
}
