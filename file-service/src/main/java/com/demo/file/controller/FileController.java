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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
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
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
    public FileUploadResponse uploadAttachment(
            @Parameter(description = "Attachment file")
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        return fileService.upload(file, BUCKET_ATTACHMENTS, jwt != null ? jwt.getSubject() : null);
    }

    /**
     * Streams the raw file bytes from MinIO, authenticated via the gateway JWT.
     * Uses {@link StreamingResponseBody} with try-with-resources so the MinIO
     * InputStream is always closed — even if the client disconnects mid-transfer.
     * Prefer this over the presigned-URL endpoint for in-browser image display,
     * as it avoids direct browser-to-MinIO requests and respects gateway CORS policy.
     */
    @Operation(summary = "Download raw file bytes")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "File bytes returned"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "File not found")
    })
    @GetMapping("/{fileId}/download")
    public ResponseEntity<StreamingResponseBody> download(
            @Parameter(description = "File UUID") @PathVariable UUID fileId) {
        FileService.DownloadResult result = fileService.download(fileId);
        StreamingResponseBody body = outputStream -> {
            try (var stream = result.inputStream()) {
                stream.transferTo(outputStream);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .body(body);
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
     * Deletes the file record and removes the object from MinIO.
     * Only the original uploader or an ADMIN may delete a file.
     */
    @Operation(summary = "Delete a file")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.NO_CONTENT, description = "File deleted"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "File not found"),
            @ApiResponse(responseCode = "403", description = "Caller is not the uploader and not an admin")
    })
    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void delete(@Parameter(description = "File UUID") @PathVariable UUID fileId,
                       @AuthenticationPrincipal Jwt jwt) {
        String callerSubject = jwt != null ? jwt.getSubject() : null;
        boolean isAdmin = isAdmin(jwt);
        fileService.delete(fileId, callerSubject, isAdmin);
    }

    /**
     * Returns true if the caller is an ADMIN — checks Spring Security authorities first
     * (covers both JWT-issued and test-injected authentication), then falls back to
     * the {@code realm_access.roles} JWT claim for Keycloak-issued tokens.
     */
    @SuppressWarnings("unchecked")
    private boolean isAdmin(Jwt jwt) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return true;
        }
        if (jwt == null) return false;
        var realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof java.util.Map<?, ?> map)) return false;
        var roles = map.get("roles");
        if (!(roles instanceof java.util.List<?> list)) return false;
        return list.contains("ADMIN");
    }

    /** Simple wrapper so the URL response is a JSON object, not a bare string. */
    public record PresignedUrlResponse(String url) {}
}
