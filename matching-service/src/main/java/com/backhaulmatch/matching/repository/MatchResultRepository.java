package com.backhaulmatch.matching.repository;

import com.backhaulmatch.matching.entity.MatchResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {
    List<MatchResult> findByMatchRequestIdOrderByMatchScoreDesc(Long matchRequestId);

    // "Duplicate match prevention": every active (PENDING_CONFIRMATION / ACCEPTED)
    // result for a set of trucks, ACROSS ALL requests — a truck that's mid-booking
    // or already confirmed for one shipment is never recommended to another.
    List<MatchResult> findByTruckAvailabilityIdInAndStatusIn(Collection<Long> truckAvailabilityIds, Collection<MatchResult.Status> statuses);

    // "Booking Requests" page in the Fleet Portal — pending confirmations for this fleet company.
    List<MatchResult> findByFleetCompanyIdAndStatusOrderByCreatedAtDesc(Long fleetCompanyId, MatchResult.Status status);

    // "Booking Requests" page — RESULTS a courier is considering (RECOMMENDED) or has already
    // reserved (PENDING_CONFIRMATION) against one of this fleet company's trucks.
    List<MatchResult> findByFleetCompanyIdAndStatusInOrderByCreatedAtDesc(Long fleetCompanyId, Collection<MatchResult.Status> statuses);

    // "Platform Reports" — successful bookings, platform-wide.
    long countByStatus(MatchResult.Status status);

    // "Admin Dashboard" — total bookings = any result a courier actually acted on.
    long countByStatusIn(Collection<MatchResult.Status> statuses);
}
