package com.backhaulmatch.auth.client;

import com.backhaulmatch.auth.dto.AdminAuditDtos.AdminAuditRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Posts login/register events to admin-service's internal audit endpoint
 * (Eureka name, not through the Gateway). Best-effort on purpose: an audit
 * write must never block or fail a login/register — if admin-service is
 * down, we log and move on.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminAuditClient {

    private final RestTemplate restTemplate;
    private static final String AUDIT_URL = "http://ADMIN-SERVICE/api/admin/audit/internal";

    public void send(AdminAuditRequest request) {
        try {
            restTemplate.postForObject(AUDIT_URL, request.withSource("auth-service"), Void.class);
        } catch (Exception e) {
            log.warn("Could not post audit event {} for user {}: {}",
                    request.action(), request.username(), e.getMessage());
        }
    }
}
