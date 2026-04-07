package com.demo.user.service;

import com.demo.common.dto.PageResponse;
import com.demo.common.dto.UserDto;
import com.demo.common.exception.DuplicateResourceException;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.common.dto.UserRequest;
import com.demo.user.model.User;
import com.demo.user.event.UserEventPublisher;
import com.demo.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserEventPublisher eventPublisher;

    public UserService(UserRepository userRepository,
                       UserEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /** Returns a paginated page of users. */
    public PageResponse<UserDto> findAll(Pageable pageable) {
        Page<User> page = userRepository.findAll(pageable);
        return new PageResponse<>(
                page.getContent().stream().map(this::toDto).toList(),
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

    /** Returns the active user with the given username, or empty if not found. */
    public Optional<UserDto> findByUsername(String username) {
        return userRepository.findByUsernameAndDeletedAtIsNull(username).map(this::toDto);
    }

    /** Returns users whose IDs are in the given collection; used by task-service batch fetch. */
    public List<UserDto> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return userRepository.findAllById(ids).stream().map(this::toDto).toList();
    }

    /** Creates and persists a new user; throws {@link DuplicateResourceException} if the username is already taken. */
    @Transactional
    public UserDto create(UserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already taken: " + request.getUsername());
        }
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .username(request.getUsername())
                .active(true) // new users are always active
                .build();
        UserDto created = toDto(userRepository.save(user));
        eventPublisher.publishCreated(created.getId(), created.getName(), created.getEmail(),
                created.getUsername(), created.isActive());
        return created;
    }

    /** Updates name, email and active flag. Username is immutable after creation and is ignored here. */
    @Transactional
    public UserDto update(UUID id, UserRequest request) {
        User user = getOrThrow(id);
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setActive(request.isActive());
        UserDto updated = toDto(userRepository.save(user));
        eventPublisher.publishUpdated(updated.getId(), updated.getName(), updated.getEmail(),
                updated.getUsername(), updated.isActive());
        return updated;
    }

    /**
     * Sets the user's preferred UI language.
     *
     * @param language ISO 639-1 code, e.g. "en" or "hr"
     */
    public UserDto updateLanguage(UUID userId, String language) {
        User user = getOrThrow(userId);
        user.setLanguage(language);
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

    /** Soft-deletes the user. */
    @Transactional
    public void delete(UUID id) {
        getOrThrow(id);
        userRepository.deleteById(id);
        eventPublisher.publishDeleted(id);
    }

    User getOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    UserDto toDto(User user) {
        return new UserDto(user.getId(), user.getName(), user.getEmail(), user.getUsername(),
                user.isActive(), user.getAvatarFileId(), user.getLanguage());
    }
}
