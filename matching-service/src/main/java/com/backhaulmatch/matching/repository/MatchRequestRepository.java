package com.backhaulmatch.matching.repository;

import com.backhaulmatch.matching.entity.MatchRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {
    List<MatchRequest> findByShipmentId(Long shipmentId);
    List<MatchRequest> findByRequestedByUserIdOrderByCreatedAtDesc(Long userId);

    // Duplicate-match guard: returns the existing live request for a shipment
    // (any that isn't CANCELLED) so a repeated/concurrent request for the same
    // shipment short-circuits instead of spawning a second matching flow.
    Optional<MatchRequest> findFirstByShipmentIdAndStatusNot(Long shipmentId, MatchRequest.Status status);

    // The "waiting-for-match" pool: every request that's still actively hunting
    // for a truck. The scheduled rechecker + the fleet-publish trigger re-run the
    // engine against these whenever new availability appears. NO_MATCH is NOT in
    // this list — that's the terminal (expired) state.
    List<MatchRequest> findByStatus(MatchRequest.Status status);

    // "Courier Reports" / "Platform Reports" — plain SQL-style aggregate counts,
    // no separate report table needed.
    long countByRequestedByUserIdAndStatus(Long requestedByUserId, MatchRequest.Status status);
    long count(); // total matches, platform-wide (inherited, listed for clarity)
}
