package com.demo.file.repository;

import com.demo.file.model.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Persistence layer for {@link FileMetadata}. */
public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {
}
