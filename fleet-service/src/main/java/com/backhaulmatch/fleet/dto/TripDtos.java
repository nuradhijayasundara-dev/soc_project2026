package com.backhaulmatch.fleet.dto;

import jakarta.validation.constraints.NotNull;

public class TripDtos {

    // "Driver assignment" + "trip creation" combined: pick a truck, a driver,
    // and (optionally) the shipment this trip is fulfilling.
    public record CreateTripRequest(
            @NotNull Long truckId,
            @NotNull Long driverId,
            Long shipmentId
    ) {}

    public record AssignDriverRequest(
            @NotNull Long driverId
    ) {}
}
