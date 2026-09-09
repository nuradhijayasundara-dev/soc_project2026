package com.backhaulmatch.fleet.controller;

import com.backhaulmatch.fleet.client.PaymentServiceClient;
import com.backhaulmatch.fleet.dto.FleetDtos.DashboardSummary;
import com.backhaulmatch.fleet.entity.Driver;
import com.backhaulmatch.fleet.entity.Trip;
import com.backhaulmatch.fleet.entity.Truck;
import com.backhaulmatch.fleet.repository.DriverRepository;
import com.backhaulmatch.fleet.repository.TripRepository;
import com.backhaulmatch.fleet.repository.TruckRepository;
import com.backhaulmatch.fleet.service.FleetCompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fleet/dashboard")
@RequiredArgsConstructor
public class FleetDashboardController {

    private final FleetCompanyService companyService;
    private final TruckRepository truckRepository;
    private final DriverRepository driverRepository;
    private final TripRepository tripRepository;
    private final PaymentServiceClient paymentServiceClient;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummary> summary(@RequestHeader("X-User-Id") Long userId) {
        Long companyId = companyService.resolveCompanyId(userId);
        List<Truck> trucks = truckRepository.findByFleetCompanyId(companyId);

        long totalTrucks = trucks.size();

        // "Active" drivers = currently out on a trip (ON_TRIP), not idle/off-duty.
        long activeDrivers = driverRepository
                .findByFleetCompanyIdAndStatus(companyId, Driver.Status.ON_TRIP)
                .size();

        List<Long> truckIds = trucks.stream().map(Truck::getId).collect(Collectors.toList());
        long activeTrips = tripRepository.findByTruckIdInOrderByStartTimeDesc(truckIds).stream()
                .filter(t -> t.getStatus() == Trip.Status.SCHEDULED
                        || t.getStatus() == Trip.Status.IN_PROGRESS)
                .count();

        BigDecimal revenueMtd = paymentServiceClient.getMonthToDateRevenue(companyId);

        return ResponseEntity.ok(new DashboardSummary(totalTrucks, activeDrivers, activeTrips, revenueMtd));
    }
}
