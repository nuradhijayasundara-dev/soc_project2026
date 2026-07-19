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
            String licenseNo
    ) {}

    public record AvailabilityRequest(
            @NotBlank String routeFrom,
            @NotBlank String routeTo,
            @NotNull LocalDateTime availableFrom,
            @NotNull BigDecimal availableCapacityTon
    ) {}

    public record DashboardSummary(
            long trucksOnline,
            BigDecimal availableCapacityTon,
            long activeBookings
    ) {}
}
