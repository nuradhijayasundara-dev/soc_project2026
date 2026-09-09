package com.backhaulmatch.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public class AdminDtos {

    // Admin Dashboard — the four headline numbers + matching detail behind them.
    public record DashboardSummaryResponse(
            long totalCourierCompanies,
            long totalFleetCompanies,
            long totalUsers,
            long totalBookings,
            long totalMatches,
            BigDecimal totalCapacityUtilizedTon
    ) {}

    // "Companies" page — both lists in one call; each row is a company view.
    public record CompanyListResponse(
            List<CompanyView> courier,
            List<CompanyView> fleet
    ) {}

    public record CompanyView(
            Long id,
            Long userId,
            String companyName,
            String registrationNo,
            String contactPhone,
            String address,
            String approvalStatus,
            String createdAt
    ) {}

    // Batch save for the Settings page: [{ key, value }, ...]
    public record SettingUpdate(
            String key,
            String value
    ) {}

    public record SettingBatchUpdate(
            List<SettingUpdate> settings
    ) {}

    // Incoming audit events posted by the Gateway and auth-service.
    public record InternalAuditRequest(
            String type,
            Long userId,
            String username,
            String action,
            String details,
            String source
    ) {}
}
