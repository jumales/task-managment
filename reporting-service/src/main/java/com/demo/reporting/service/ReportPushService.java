package com.demo.reporting.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Sends real-time push notifications to connected WebSocket clients after the reporting
 * projections are updated. Each user subscribes to {@code /topic/reports/{userId}};
 * the payload is a simple signal telling the frontend to re-fetch its data.
 */
@Service
public class ReportPushService {

    private static final Logger log = LoggerFactory.getLogger(ReportPushService.class);
    private static final String TOPIC_PREFIX = "/topic/reports/";

    private final SimpMessagingTemplate messagingTemplate;

    public ReportPushService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Notifies the given user that their report data has changed. The frontend
     * receives this and re-fetches the relevant queries.
     */
    public void notifyUser(UUID userId) {
        if (userId == null) return;
        String destination = TOPIC_PREFIX + userId;
        log.debug("Pushing report update to {}", destination);
        messagingTemplate.convertAndSend(destination, "{\"type\":\"REPORT_UPDATED\"}");
    }
}
