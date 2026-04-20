package com.demo.audit.controller;

import com.demo.audit.service.DltLagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes a lightweight endpoint to inspect dead-letter topic consumer lag.
 * A non-zero lag on any DLT indicates messages that failed all retries and have not yet
 * been processed by a DLT consumer — they require manual investigation or replay.
 */
@Tag(name = "DLQ", description = "Dead-letter queue monitoring")
@RestController
@RequestMapping("/api/v1/dlq")
public class DlqController {

    private final DltLagService dltLagService;

    public DlqController(DltLagService dltLagService) {
        this.dltLagService = dltLagService;
    }

    /**
     * Returns unconsumed-message lag per DLT topic.
     * Lag is {@code end_offset − committed_offset} for the DLT's dedicated consumer group,
     * so it drops back to zero once messages are processed or replayed.
     * A value of {@code -1} means the AdminClient call failed.
     */
    @Operation(
        summary = "Get DLT consumer lag",
        description = "Returns unconsumed-message count per DLT topic. Non-zero values indicate "
                    + "failed messages that exhausted all retries and need investigation or replay. "
                    + "Unlike end offset, this drops to zero once messages are processed."
    )
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getDltStatus() {
        Map<String, Long> lags = dltLagService.getAllLags();
        return Map.of(
            "dltConsumerLag", lags,
            "note", "Unconsumed messages per DLT topic (end offset − committed offset). "
                  + "0 means no pending failures. -1 means AdminClient call failed."
        );
    }
}
