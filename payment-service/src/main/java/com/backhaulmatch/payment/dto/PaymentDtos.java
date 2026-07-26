package com.backhaulmatch.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class PaymentDtos {

    // Called internally by matching-service the instant a booking is accepted.
    public record CreateInvoiceRequest(
            @NotNull Long shipmentId,
            Long matchResultId,
            @NotNull Long courierUserId,
            @NotNull Long fleetCompanyId,
            String truckNo,
            Double distanceKm,
            BigDecimal weightKg
    ) {}

    // "Payment API" — courier pays an invoice (simulated, no real gateway).
    public record PayInvoiceRequest(
            @NotBlank String method // CARD | BANK_TRANSFER | CASH
    ) {}

    // "Courier Reports" — cost savings piece. Reused by courier-service's report endpoint.
    public record CourierCostSummaryResponse(
            long paidInvoiceCount,
            BigDecimal totalSpent,
            BigDecimal costSavings
    ) {}

    // "Fleet revenue display" / "booking revenue dashboard"
    public record RevenueSummaryResponse(
            long totalBookings,
            long paidBookings,
            long pendingBookings,
            BigDecimal totalRevenue,   // sum of PAID invoices
            BigDecimal pendingRevenue  // sum of PENDING invoices
    ) {}
}
