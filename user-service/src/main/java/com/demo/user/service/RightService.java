package com.demo.user.service;

import com.demo.common.dto.RightDto;
import com.demo.common.exception.RelatedEntityActiveException;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.common.dto.RightRequest;
import com.demo.user.config.CacheConfig;
import com.demo.user.model.Right;
import com.demo.user.repository.RightRepository;
import com.demo.user.repository.RoleRightRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RightService {

    private final RightRepository repository;
    private final RoleRightRepository roleRightRepository;

    public RightService(RightRepository repository, RoleRightRepository roleRightRepository) {
        this.repository = repository;
        this.roleRightRepository = roleRightRepository;
    }

    /** Returns all rights; result is cached under the rights cache. */
    @Cacheable(CacheConfig.RIGHTS)
    public List<RightDto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    /** Returns the right with the given ID; result is cached by ID. */
    @Cacheable(value = CacheConfig.RIGHTS, key = "#id")
    public RightDto findById(UUID id) {
        return toDto(getOrThrow(id));
    }

    /** Creates and persists a new right and evicts the rights cache. */
    @CacheEvict(value = CacheConfig.RIGHTS, allEntries = true)
    public RightDto create(RightRequest request) {
        Right right = Right.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        return toDto(repository.save(right));
    }

    /** Soft-deletes the right; evicts both rights and roles caches, and throws if any roles still reference it. */
    // Evicts both caches: deleting a right makes cached role responses stale
    // because roles embed their granted rights.
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.RIGHTS, allEntries = true),
            @CacheEvict(value = CacheConfig.ROLES,  allEntries = true)
    })
    public void delete(UUID id) {
        Right right = getOrThrow(id);
        if (roleRightRepository.existsByRight(right)) {
            throw new RelatedEntityActiveException("Right", "roles");
        }
        repository.deleteById(id);
    }

    /** Returns the raw {@link com.demo.user.model.Right} entity, or throws {@link com.demo.common.exception.ResourceNotFoundException}. */
    public Right getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Right", id));
    }

    /** Converts a {@link com.demo.user.model.Right} entity to its DTO representation. */
    public RightDto toDto(Right right) {
        return new RightDto(right.getId(), right.getName(), right.getDescription());
    }
}
