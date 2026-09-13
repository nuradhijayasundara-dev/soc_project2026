package com.backhaulmatch.fleet.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TripDtos {

    // "Driver assignment" + "trip creation" combined: pick a truck, a driver,
    // and (optionally) the shipment this trip is fulfilling. driverId is optional —
    // a Trip created automatically when a booking is accepted (see /trips/internal)
    // doesn't have a driver yet; the fleet manager assigns one afterwards via
    // PATCH /{id}/assign-driver.
    public record CreateTripRequest(
            @NotNull Long truckId,
            Long driverId,
            Long shipmentId
    ) {}

    // Used internally by matching-service the moment a booking is accepted —
    // just the truck and the shipment it's fulfilling; no driver yet.
    public record CreateTripFromBookingRequest(
            @NotNull Long truckId,
            @NotNull Long shipmentId
    ) {}

    public record AssignDriverRequest(
            @NotNull Long driverId
    ) {}

    // What the Driver App needs to render a trip: it's joined with fleet-service's
    // own truck record and courier-service's shipment so the driver sees truck no,
    // pickup → destination, the planned route and its distances — no extra calls.
    public record DriverTripResponse(
            Long id,
            Long truckId,
            String truckNo,
            Long driverId,
            Long shipmentId,
            String status,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String shipmentCode,
            String pickupLocation,
            String destination,
            Double pickupLatitude,
            Double pickupLongitude,
            Double destinationLatitude,
            Double destinationLongitude,
            BigDecimal distanceKm,
            String estimatedDuration,
            Double weightKg
    ) {}
}
