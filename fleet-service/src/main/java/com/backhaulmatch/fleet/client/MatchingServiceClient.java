package com.backhaulmatch.fleet.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Service-to-service call to matching-service (Eureka name), used the instant a
 * fleet manager publishes new availability. It tells matching-service to
 * re-check every WAITING_FOR_MATCH shipment against the freshly posted truck —
 * so a courier who previously found nothing gets auto-matched (and notified)
 * without having to resubmit. Best-effort: failures are logged, not thrown,
 * because the scheduled rechecker in matching-service is the safety net.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MatchingServiceClient {

    private final RestTemplate restTemplate;
    private static final String RECHECK_URL = "http://MATCHING-SERVICE/api/matching/waiting/recheck";

    public void triggerWaitingRecheck() {
        try {
            restTemplate.postForObject(RECHECK_URL, null, Void.class);
        } catch (Exception e) {
            log.warn("Could not trigger waiting-for-match recheck in matching-service: {}", e.getMessage());
        }
    }
}
