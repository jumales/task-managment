package com.demo.user.service;

import com.demo.common.dto.RoleDto;
import com.demo.common.exception.DuplicateResourceException;
import com.demo.common.exception.RelatedEntityActiveException;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.common.dto.RoleRequest;
import com.demo.user.config.CacheConfig;
import com.demo.user.model.Right;
import com.demo.user.model.Role;
import com.demo.user.model.RoleRight;
import com.demo.user.repository.RoleRepository;
import com.demo.user.repository.RoleRightRepository;
import com.demo.user.repository.UserRoleRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final RoleRightRepository roleRightRepository;
    private final UserRoleRepository userRoleRepository;
    private final RightService rightService;

    public RoleService(RoleRepository roleRepository,
                       RoleRightRepository roleRightRepository,
                       UserRoleRepository userRoleRepository,
                       RightService rightService) {
        this.roleRepository = roleRepository;
        this.roleRightRepository = roleRightRepository;
        this.userRoleRepository = userRoleRepository;
        this.rightService = rightService;
    }

    /** Returns all roles; result is cached under the roles cache. */
    @Cacheable(CacheConfig.ROLES)
    public List<RoleDto> findAll() {
        return roleRepository.findAll().stream().map(this::toDto).toList();
    }

    /** Returns the role with the given ID; result is cached by ID. */
    @Cacheable(value = CacheConfig.ROLES, key = "#id")
    public RoleDto findById(UUID id) {
        return toDto(getOrThrow(id));
    }

    /** Creates and persists a new role and evicts the roles cache. */
    @CacheEvict(value = CacheConfig.ROLES, allEntries = true)
    public RoleDto create(RoleRequest request) {
        Role role = Role.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        return toDto(roleRepository.save(role));
    }

    /** Soft-deletes the role; throws if any users or rights are still associated with it. */
    @CacheEvict(value = CacheConfig.ROLES, allEntries = true)
    public void delete(UUID id) {
        Role role = getOrThrow(id);
        if (userRoleRepository.existsByRole(role)) {
            throw new RelatedEntityActiveException("Role", "assigned users");
        }
        if (roleRightRepository.existsByRole(role)) {
            throw new RelatedEntityActiveException("Role", "granted rights");
        }
        roleRepository.deleteById(id);
    }

    /**
     * Grants a right to a role; throws if the right is already granted.
     *
     * @param grantedBy identifier of the actor performing the grant
     */
    @CacheEvict(value = CacheConfig.ROLES, allEntries = true)
    @Transactional
    public RoleDto grantRight(UUID roleId, UUID rightId, String grantedBy) {
        Role role = getOrThrow(roleId);
        Right right = rightService.getOrThrow(rightId);

        if (roleRightRepository.existsByRoleAndRight(role, right)) {
            throw new DuplicateResourceException("Role '" + role.getName() + "' already has right: " + right.getName());
        }

        roleRightRepository.save(RoleRight.builder()
                .role(role)
                .right(right)
                .grantedBy(grantedBy)
                .build());

        return toDto(role);
    }

    /** Soft-deletes the right grant from the specified role. */
    @CacheEvict(value = CacheConfig.ROLES, allEntries = true)
    @Transactional
    public void revokeRight(UUID roleId, UUID rightId) {
        Role role = getOrThrow(roleId);
        Right right = rightService.getOrThrow(rightId);
        roleRightRepository.softDeleteByRoleAndRight(role, right);
    }

    /**
     * Batch-converts a collection of {@link Role} entities to a {@code Map<id, RoleDto>},
     * loading all rights in a single query. Use this in list methods to avoid N+1 queries.
     */
    public Map<UUID, RoleDto> toDtoMap(Collection<Role> roles) {
        if (roles.isEmpty()) return Map.of();
        Map<UUID, List<com.demo.common.dto.RightDto>> rightsByRoleId = roleRightRepository.findByRoleIn(roles)
                .stream()
                .collect(Collectors.groupingBy(
                        rr -> rr.getRole().getId(),
                        Collectors.mapping(rr -> rightService.toDto(rr.getRight()), Collectors.toList())));
        return roles.stream()
                .collect(Collectors.toMap(
                        Role::getId,
                        r -> new RoleDto(r.getId(), r.getName(), r.getDescription(),
                                rightsByRoleId.getOrDefault(r.getId(), List.of()))));
    }

    /** Returns the raw {@link com.demo.user.model.Role} entity, or throws {@link com.demo.common.exception.ResourceNotFoundException}. */
    public Role getOrThrow(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id));
    }

    /** Converts a {@link com.demo.user.model.Role} entity to its DTO, including all granted rights. */
    public RoleDto toDto(Role role) {
        var rights = roleRightRepository.findByRole(role).stream()
                .map(rr -> rightService.toDto(rr.getRight()))
                .toList();
        return new RoleDto(role.getId(), role.getName(), role.getDescription(), rights);
    }
}
