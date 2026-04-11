---
name: move-dtos-to-common
description: Scan all service modules for misplaced DTO classes (*Request.java, *Response.java, *Dto.java) that belong in the common module, move them, and update every import across the project.
---
Scan the current multi-module Maven project for any DTO classes (files matching `*Request.java`, `*Response.java`, `*Dto.java`) that live inside a service-specific package (e.g. `com.demo.<service>.dto`) instead of the shared `common` module (`com.demo.common.dto`).

For each misplaced DTO:

1. **Read** the file to capture its full content.
2. **Create** the equivalent file under `common/src/main/java/com/demo/common/dto/` with the package declaration updated to `com.demo.common.dto`.
3. **Delete** the original file from the service-specific package.
4. **Find all files** across the entire project that import the old package path for this DTO.
5. **Update every import** from `com.demo.<service>.dto.<ClassName>` to `com.demo.common.dto.<ClassName>`.

After processing all misplaced DTOs:
- Confirm that no file in the project still imports from a service-specific `dto` package.
- List every file that was created, deleted, or had its imports updated.
