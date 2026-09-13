package com.backhaulmatch.admin.client;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Fetches platform matching/booking aggregates from matching-service
 * (Eureka name) for the Admin Dashboard.
 */
@Component
@RequiredArgsConstructor
public class MatchingServiceClient {

    private final RestTemplate restTemplate;
    private static final String SUMMARY_URL = "http://MATCHING-SERVICE/internal/admin/reports/summary";

    @SuppressWarnings("unchecked")
    public Map<String, Object> getAdminSummary() {
        return restTemplate.getForObject(SUMMARY_URL, Map.class);
    }
}
