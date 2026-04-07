package com.demo.task.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

/**
 * Feign client for file-service file management operations.
 * The caller must forward the user's Bearer token so file-service can
 * verify the caller is the original uploader or an ADMIN before deleting.
 */
@FeignClient(name = "file-service")
public interface FileClient {

    /**
     * Deletes the file record and removes the object from MinIO.
     *
     * @param fileId      UUID of the file to delete
     * @param bearerToken the caller's {@code Authorization: Bearer <token>} header
     */
    @DeleteMapping("/api/v1/files/{fileId}")
    void deleteFile(@PathVariable UUID fileId, @RequestHeader("Authorization") String bearerToken);
}
