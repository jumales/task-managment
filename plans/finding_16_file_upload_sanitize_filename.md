# Finding #16 — Sanitize uploaded filename to prevent path traversal

## Status
UNRESOLVED

## Severity
MEDIUM — path traversal risk in MinIO object key and DB-stored filename

## Context
`FileService.upload()` constructs the MinIO object key as:
```java
String objectKey = fileId + "_" + file.getOriginalFilename();   // line 65
```
`MultipartFile.getOriginalFilename()` returns the client-provided filename verbatim. A malicious
client can send `filename=../../etc/passwd` or a name containing null bytes or path separators.
The unsanitized name is also stored in the `files` table (`original_filename` column) and returned
to callers. While the UUID prefix reduces practical exploitability, the defensive correct fix is
one line.

## Root Cause
`file-service/src/main/java/com/demo/file/service/FileService.java:65`

```java
String objectKey = fileId + "_" + file.getOriginalFilename();   // ← raw client input
```

And line 75 (approximate):
```java
.originalFilename(file.getOriginalFilename())   // ← stored in DB without sanitization
```

## Files to Modify

### `file-service/src/main/java/com/demo/file/service/FileService.java`
```java
// Add import (if not already present):
import java.nio.file.Paths;

// In upload() method, replace line 65:

// Before:
String objectKey = fileId + "_" + file.getOriginalFilename();

// After:
String rawFilename = file.getOriginalFilename();
String safeFilename = (rawFilename != null && !rawFilename.isBlank())
        ? Paths.get(rawFilename).getFileName().toString()   // strips directory components
        : "upload";
String objectKey = fileId + "_" + safeFilename;
```

Also update the line that persists `original_filename` to use `safeFilename` instead of
`file.getOriginalFilename()`.

`Paths.get(name).getFileName()` strips all leading directory components:
- `../../etc/passwd` → `passwd`
- `subdir/file.pdf` → `file.pdf`
- `file.pdf` → `file.pdf` (unchanged for normal filenames)

## Verification
1. Upload a file with filename `../../etc/passwd`
2. Check MinIO — object key should be `{uuid}_passwd`, not `{uuid}_../../etc/passwd`
3. Check DB `original_filename` column — should store `passwd`
4. Normal file upload (`photo.jpg`) must be unaffected
5. Existing file upload IT tests must pass

## Notes
- `Paths.get(name).getFileName()` returns null only if the path is the empty path — the null check above handles this
- Null bytes in filenames: Java's `String` allows null bytes; add `.replace("\0", "")` as an extra guard if needed
- No Flyway migration needed — existing rows in DB are unaffected; only new uploads use the safe name
- This is a one-method change in a single file
