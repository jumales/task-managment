package com.demo.file.controller;

import com.demo.common.dto.FileUploadResponse;
import com.demo.common.web.ResponseCode;
import com.demo.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * REST endpoints for file upload and URL resolution.
 * Files are stored in MinIO; metadata is persisted in PostgreSQL.
 */
@Tag(name = "Files", description = "Upload files and resolve download URLs")
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private static final String BUCKET_AVATARS     = "avatars";
    private static final String BUCKET_ATTACHMENTS = "attachments";

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * Uploads a file to the avatars bucket and returns the file metadata.
     * Use the returned {@code fileId} when calling PATCH /api/v1/users/{id}/avatar.
     */
    @Operation(summary = "Upload an avatar image")
    @ApiResponse(responseCode = ResponseCode.CREATED, description = "File uploaded")
    @PostMapping(value = "/avatars", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public FileUploadResponse uploadAvatar(
            @Parameter(description = "Image file (JPEG, PNG, GIF, WebP)")
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        return fileService.upload(file, BUCKET_AVATARS, jwt != null ? jwt.getSubject() : null);
    }

    /**
     * Uploads a file to the attachments bucket and returns the file metadata.
     */
    @Operation(summary = "Upload a task attachment")
    @ApiResponse(responseCode = ResponseCode.CREATED, description = "File uploaded")
    @PostMapping(value = "/attachments", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public FileUploadResponse uploadAttachment(
            @Parameter(description = "Attachment file")
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        return fileService.upload(file, BUCKET_ATTACHMENTS, jwt != null ? jwt.getSubject() : null);
    }

    /**
     * Streams the raw file bytes from MinIO, authenticated via the gateway JWT.
     * Prefer this over the presigned-URL endpoint for in-browser image display,
     * as it avoids direct browser-to-MinIO requests and respects gateway CORS policy.
     */
    @Operation(summary = "Download raw file bytes")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "File bytes returned"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "File not found")
    })
    @GetMapping("/{fileId}/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @Parameter(description = "File UUID") @PathVariable UUID fileId) {
        FileService.DownloadResult result = fileService.download(fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .body(result.resource());
    }

    /**
     * Returns a short-lived presigned URL for downloading the file.
     * The URL expires after 1 hour; clients should not cache it permanently.
     */
    @Operation(summary = "Get a presigned download URL for a file")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "URL generated"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "File not found")
    })
    @GetMapping("/{fileId}/url")
    public PresignedUrlResponse getPresignedUrl(
            @Parameter(description = "File UUID") @PathVariable UUID fileId) {
        return new PresignedUrlResponse(fileService.getPresignedUrl(fileId));
    }

    /**
     * Soft-deletes the file record (object in MinIO is not removed).
     */
    @Operation(summary = "Delete a file")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.NO_CONTENT, description = "File deleted"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "File not found")
    })
    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(description = "File UUID") @PathVariable UUID fileId) {
        fileService.delete(fileId);
    }

    /** Simple wrapper so the URL response is a JSON object, not a bare string. */
    public record PresignedUrlResponse(String url) {}
}
