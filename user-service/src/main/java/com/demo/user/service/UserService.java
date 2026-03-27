package com.demo.user.service;

import com.demo.common.dto.PageResponse;
import com.demo.common.dto.RoleDto;
import com.demo.common.dto.UserDto;
import com.demo.common.exception.DuplicateResourceException;
import com.demo.common.exception.RelatedEntityActiveException;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.common.dto.UserRequest;
import com.demo.user.model.Role;
import com.demo.user.model.User;
import com.demo.user.model.UserRole;
import com.demo.user.repository.UserRepository;
import com.demo.user.repository.UserRoleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleService roleService;

    public UserService(UserRepository userRepository,
                       UserRoleRepository userRoleRepository,
                       RoleService roleService) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleService = roleService;
    }

    /** Returns a paginated page of users. */
    public PageResponse<UserDto> findAll(Pageable pageable) {
        Page<User> page = userRepository.findAll(pageable);
        return new PageResponse<>(
                toDtoList(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    /** Returns the user with the given ID, or throws {@link com.demo.common.exception.ResourceNotFoundException}. */
    public UserDto findById(UUID id) {
        return toDto(getOrThrow(id));
    }

    /** Returns users whose IDs are in the given collection; used by task-service batch fetch. */
    public List<UserDto> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return toDtoList(userRepository.findAllById(ids));
    }

    /** Creates and persists a new user; throws {@link DuplicateResourceException} if the username is already taken. */
    public UserDto create(UserRequest request) {
        if (request.getUsername() != null && userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already taken: " + request.getUsername());
        }
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .username(request.getUsername())
                .active(true) // new users are always active
                .build();
        return toDto(userRepository.save(user));
    }

    /** Updates name, email and active flag. Username is immutable after creation and is ignored here. */
    public UserDto update(UUID id, UserRequest request) {
        User user = getOrThrow(id);
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setActive(request.isActive());
        return toDto(userRepository.save(user));
    }

    /**
     * Sets the user's avatar to the file identified by {@code fileId}.
     * Pass {@code null} to remove the avatar (revert to default placeholder).
     */
    public UserDto updateAvatar(UUID userId, UUID fileId) {
        User user = getOrThrow(userId);
        user.setAvatarFileId(fileId);
        return toDto(userRepository.save(user));
    }

    /** Soft-deletes the user; throws if the user still has active role assignments. */
    public void delete(UUID id) {
        User user = getOrThrow(id);
        if (userRoleRepository.existsByUser(user)) {
            throw new RelatedEntityActiveException("User", "roles");
        }
        userRepository.deleteById(id);
    }

    /**
     * Assigns the specified role to the user; throws if the role is already assigned.
     *
     * @param assignedBy identifier of the actor performing the assignment
     */
    @Transactional
    public UserDto assignRole(UUID userId, UUID roleId, String assignedBy) {
        User user = getOrThrow(userId);
        var role = roleService.getOrThrow(roleId);

        if (userRoleRepository.existsByUserAndRole(user, role)) {
            throw new DuplicateResourceException("User already has role: " + role.getName());
        }

        userRoleRepository.save(UserRole.builder()
                .user(user)
                .role(role)
                .assignedBy(assignedBy)
                .build());

        return toDto(user);
    }

    /** Soft-deletes the role assignment between the specified user and role. */
    @Transactional
    public void revokeRole(UUID userId, UUID roleId) {
        User user = getOrThrow(userId);
        var role = roleService.getOrThrow(roleId);
        userRoleRepository.softDeleteByUserAndRole(user, role);
    }

    private User getOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private UserDto toDto(User user) {
        var roles = userRoleRepository.findByUser(user).stream()
                .map(ur -> roleService.toDto(ur.getRole()))
                .toList();
        return new UserDto(user.getId(), user.getName(), user.getEmail(), user.getUsername(), user.isActive(), roles, user.getAvatarFileId());
    }

    /**
     * Batch-converts a list of users to DTOs using two queries total (one for roles, one for rights),
     * avoiding N+1 database round-trips.
     */
    private List<UserDto> toDtoList(List<User> users) {
        if (users.isEmpty()) return List.of();

        // Load all role assignments for these users in one query
        List<UserRole> userRoles = userRoleRepository.findByUserIn(users);
        Set<Role> roles = userRoles.stream().map(UserRole::getRole).collect(Collectors.toSet());

        // Load all rights for these roles in one query
        Map<UUID, RoleDto> roleDtoById = roleService.toDtoMap(roles);

        // Group role DTOs by user ID
        Map<UUID, List<RoleDto>> rolesByUserId = userRoles.stream()
                .collect(Collectors.groupingBy(
                        ur -> ur.getUser().getId(),
                        Collectors.mapping(ur -> roleDtoById.get(ur.getRole().getId()), Collectors.toList())));

        return users.stream()
                .map(u -> new UserDto(u.getId(), u.getName(), u.getEmail(), u.getUsername(), u.isActive(),
                        rolesByUserId.getOrDefault(u.getId(), List.of()), u.getAvatarFileId()))
                .toList();
    }
}
