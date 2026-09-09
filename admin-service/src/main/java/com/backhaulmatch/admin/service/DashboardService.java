package com.backhaulmatch.admin.service;

import com.backhaulmatch.admin.client.AuthServiceClient;
import com.backhaulmatch.admin.client.CourierServiceClient;
import com.backhaulmatch.admin.client.FleetServiceClient;
import com.backhaulmatch.admin.client.MatchingServiceClient;
import com.backhaulmatch.admin.dto.AdminDtos.DashboardSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** Aggregates the dashboard headline numbers from the services that own the data. */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CourierServiceClient courierServiceClient;
    private final FleetServiceClient fleetServiceClient;
    private final AuthServiceClient authServiceClient;
    private final MatchingServiceClient matchingServiceClient;

    public DashboardSummaryResponse summary() {
        long courier = courierServiceClient.countCompanies();
        long fleet = fleetServiceClient.countCompanies();
        long users = authServiceClient.countUsers();

        java.util.Map<String, Object> matching = matchingServiceClient.getAdminSummary();
        long totalBookings = matching == null ? 0 : ((Number) matching.getOrDefault("totalBookings", 0)).longValue();
        long totalMatches = matching == null ? 0 : ((Number) matching.getOrDefault("totalMatches", 0)).longValue();
        BigDecimal capacity = matching == null || matching.get("totalCapacityUtilizedTon") == null
                ? BigDecimal.ZERO
                : new BigDecimal(String.valueOf(matching.get("totalCapacityUtilizedTon")));

        return new DashboardSummaryResponse(courier, fleet, users, totalBookings, totalMatches, capacity);
    }
}
