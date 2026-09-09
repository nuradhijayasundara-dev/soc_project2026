package com.backhaulmatch.matching.dto;

import jakarta.validation.constraints.NotNull;

public class MatchingDtos {

    // Courier Portal's "Request Backhaul Transport" button posts this.
    public record CreateMatchRequestDto(
            @NotNull Long shipmentId
    ) {}

    // Raw shape we read back from fleet-service's availability search — kept
    // minimal since we only need a few fields for scoring + display.
    public record AvailabilityCandidate(
            Long id,          // truck_availability.id
            Long truckId,
            Long fleetCompanyId,
            String truckNo,
            String truckType,
            String truckStatus,       // AVAILABLE / ON_TRIP / MAINTENANCE
            String routeFrom,
            String routeTo,
            String availableFrom,   // ISO string
            String expectedArrival, // ISO string — when the truck is expected at routeTo
            java.math.BigDecimal availableCapacityTon,
            String availabilityStatus  // AVAILABLE / BOOKED / EXPIRED
    ) {}
}
