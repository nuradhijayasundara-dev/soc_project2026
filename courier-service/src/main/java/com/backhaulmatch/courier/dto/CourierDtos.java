package com.backhaulmatch.courier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CourierDtos {

    public record CompanyRequest(
            @NotBlank String companyName,
            String registrationNo,
            String contactPhone,
            String address
    ) {}

    public record CustomerRequest(
            @NotBlank String fullName,
            String phone,
            String email
    ) {}

    public record ReceiverRequest(
            @NotBlank String fullName,
            String phone,
            String address
    ) {}

    // Used when creating a shipment: either pass an existing customerId, or
    // inline "newCustomer" details and inline "receiver" details in one call —
    // matches the "New Shipment Request" form in the interface map (pickup/delivery/parcel).
    public record CreateShipmentRequest(
            Long customerId,
            CustomerRequest newCustomer,
            @NotNull ReceiverRequest receiver,
            @NotBlank String pickupLocation,
            @NotNull LocalDateTime pickupDatetime,
            @NotBlank String destination,
            LocalDate deliveryDeadline,
            BigDecimal weightKg,
            String dimensions,
            String parcelType,
            String remarks
    ) {}

    public record StatusUpdateRequest(
            @NotBlank String status, // PENDING | MATCHED | IN_TRANSIT | DELIVERED | CANCELLED
            String location
    ) {}

    public record DashboardSummary(
            long totalRequests,
            long matchedShipments,
            long inTransit,
            long delivered
    ) {}
}
