package com.demo.user.repository;

import com.demo.user.model.Right;
import com.demo.user.model.Role;
import com.demo.user.model.RoleRight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRightRepository extends JpaRepository<RoleRight, UUID> {
    /** Returns all active right grants for the given role. */
    List<RoleRight> findByRole(Role role);
    /** Returns the active right grant for the given role and right, if one exists. */
    Optional<RoleRight> findByRoleAndRight(Role role, Right right);
    /** Returns {@code true} if the role has at least one active right grant. */
    boolean existsByRole(Role role);
    /** Returns {@code true} if the right is actively granted to at least one role. */
    boolean existsByRight(Right right);
    /** Returns {@code true} if the specified right is actively granted to the specified role. */
    boolean existsByRoleAndRight(Role role, Right right);

    /** Soft-deletes the right grant from the given role. */
    @Modifying
    @Query("UPDATE RoleRight rr SET rr.deletedAt = CURRENT_TIMESTAMP WHERE rr.role = :role AND rr.right = :right AND rr.deletedAt IS NULL")
    void softDeleteByRoleAndRight(@Param("role") Role role, @Param("right") Right right);
}
