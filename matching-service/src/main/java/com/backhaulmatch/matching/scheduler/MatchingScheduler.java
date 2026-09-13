package com.backhaulmatch.matching.scheduler;

import com.backhaulmatch.matching.service.MatchingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Safety-net driver for the "Waiting for Match" feature.
 *
 * The primary trigger is a fleet manager publishing new availability, which
 * calls matching-service immediately (see FleetServiceClient.triggerRecheck).
 * This scheduler is the fallback that guarantees every WAITING_FOR_MATCH
 * shipment keeps getting reconsidered even if that push ever fails or is
 * missed, and that they expire cleanly once their pickup time has passed.
 *
 * The interval is deliberately short (a university-scale dataset) so a courier
 * doesn't wait long after a fleet posts a truck; the run is cheap when there
 * are no waiting requests.
 */
@Component
@Slf4j
public class MatchingScheduler {

    private final MatchingService matchingService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public MatchingScheduler(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void recheckWaitingRequests() {
        // Never run two rechecks concurrently (e.g. a manual fleet-publish
        // trigger overlapping the scheduled tick).
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            int matched = matchingService.recheckWaitingMatches();
            if (matched > 0) {
                log.info("Waiting-for-match recheck matched {} shipment(s)", matched);
            }
        } catch (Exception e) {
            log.warn("Waiting-for-match recheck failed: {}", e.getMessage());
        } finally {
            running.set(false);
        }
    }
}
