package com.backhaulmatch.fleet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FleetDtos {

    public record CompanyRequest(
            @NotBlank String companyName,
            String registrationNo,
            String contactPhone,
            String address
    ) {}

    public record TruckRequest(
            @NotBlank String truckNo,
            @NotNull BigDecimal capacityTon,
            String truckType
    ) {}

    public record DriverRequest(
            @NotBlank String fullName,
            String phone,
            String licenseNo,
            Long userId // optional: link to the driver's auth account (role DRIVER) up front
    ) {}

    public record AvailabilityRequest(
            @NotBlank String routeFrom,
            @NotBlank String routeTo,
            @NotNull LocalDateTime availableFrom,
            @NotNull BigDecimal availableCapacityTon,
            String tripType // "OUTBOUND" | "BACKHAUL", defaults to BACKHAUL if omitted
    ) {}

    // "Post Backhaul Availability" quick-add form (fleet-portal /availability page) —
    // lets a fleet manager pick which truck without navigating into its details page first.
    // Coordinate + routing fields (startLatitude/.../distanceKm/estimatedDuration) are filled
    // in by the frontend LocationPicker + local OSRM route and are optional for backwards
    // compatibility.
    public record QuickAvailabilityRequest(
            @NotNull Long truckId,
            Long driverId,
            @NotBlank String routeFrom,
            @NotBlank String returnDestination, // routeTo, named for the backhaul UI
            @NotNull LocalDateTime availableFrom,
            @NotNull LocalDateTime expectedArrival, // availableFrom + route driving time — what couriers will see
            @NotNull BigDecimal availableCapacityTon,
            String startLocationName,
            Double startLatitude,
            Double startLongitude,
            String destinationName,
            Double destinationLatitude,
            Double destinationLongitude,
            BigDecimal distanceKm,
            String estimatedDuration
    ) {}

    // Cross-company search result shape — this is what matching-service's
    // FleetServiceClient deserializes, so field names must match exactly.
    public record AvailabilityCandidateResponse(
            Long id,
            Long truckId,
            Long fleetCompanyId,
            String truckNo,
            String truckType,
            String truckStatus,
            String routeFrom,
            String routeTo,
            LocalDateTime availableFrom,
            LocalDateTime expectedArrival,
            BigDecimal availableCapacityTon,
            String availabilityStatus
    ) {}

    public record CapacityVerificationResponse(
            boolean valid,
            BigDecimal availableCapacityTon,
            String reason // null when valid
    ) {}

    // "Fleet Reports" — total trips, used capacity (backhaul revenue comes from
    // payment-service's /revenue/summary, called separately by the frontend).
    public record FleetReportSummary(
            long totalTrips,
            BigDecimal usedCapacityTon
    ) {}

    // Fleet Dashboard cards — the four headline numbers a fleet manager sees first.
    public record DashboardSummary(
            long totalTrucks,
            long activeDrivers,
            long activeTrips,
            BigDecimal revenueMtd
    ) {}
}
