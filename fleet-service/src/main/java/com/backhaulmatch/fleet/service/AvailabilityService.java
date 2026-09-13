package com.backhaulmatch.fleet.service;

import com.backhaulmatch.fleet.dto.FleetDtos.AvailabilityCandidateResponse;
import com.backhaulmatch.fleet.dto.FleetDtos.CapacityVerificationResponse;
import com.backhaulmatch.fleet.dto.FleetDtos.QuickAvailabilityRequest;
import com.backhaulmatch.fleet.entity.Truck;
import com.backhaulmatch.fleet.entity.TruckAvailability;
import com.backhaulmatch.fleet.client.MatchingServiceClient;
import com.backhaulmatch.fleet.repository.TruckAvailabilityRepository;
import com.backhaulmatch.fleet.repository.TruckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final TruckAvailabilityRepository availabilityRepository;
    private final TruckRepository truckRepository;
    private final MatchingServiceClient matchingServiceClient;

    /** "Availability" page: every posting across all of this fleet company's trucks. */
    public List<TruckAvailability> listForCompany(Long fleetCompanyId) {
        List<Long> truckIds = truckRepository.findByFleetCompanyId(fleetCompanyId).stream()
                .map(Truck::getId).collect(Collectors.toList());
        return availabilityRepository.findByTruckIdInOrderByAvailableFromDesc(truckIds);
    }

    /** Quick-add form: fleet manager picks any of their own trucks without visiting its details page. */
    public TruckAvailability quickAdd(Long fleetCompanyId, QuickAvailabilityRequest req) {
        Truck truck = truckRepository.findById(req.truckId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Truck not found"));
        if (!truck.getFleetCompanyId().equals(fleetCompanyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That truck doesn't belong to your company");
        }

        TruckAvailability availability = new TruckAvailability();
        availability.setTruckId(truck.getId());
        availability.setDriverId(req.driverId());
        availability.setRouteFrom(req.routeFrom());
        availability.setRouteTo(req.returnDestination());
        availability.setStartLocationName(req.startLocationName());
        availability.setStartLatitude(req.startLatitude());
        availability.setStartLongitude(req.startLongitude());
        availability.setDestinationName(req.destinationName());
        availability.setDestinationLatitude(req.destinationLatitude());
        availability.setDestinationLongitude(req.destinationLongitude());
        availability.setDistanceKm(req.distanceKm());
        availability.setEstimatedDuration(req.estimatedDuration());
        availability.setAvailableFrom(req.availableFrom());
        availability.setExpectedArrival(req.expectedArrival());
        availability.setAvailableCapacityTon(req.availableCapacityTon());
        availability.setTripType(TruckAvailability.TripType.BACKHAUL);
        availability.setStatus(TruckAvailability.Status.AVAILABLE);
        TruckAvailability saved = availabilityRepository.save(availability);

        // A new backhaul slot is up — ask matching-service to reconsider every
        // WAITING_FOR_MATCH shipment right now (the scheduled rechecker is the
        // fallback if this push is ever missed). Best-effort, never blocks.
        matchingServiceClient.triggerWaitingRecheck();

        return saved;
    }

    /** Cross-company search — called directly by matching-service (service-to-service, not via Gateway). */
    public List<AvailabilityCandidateResponse> search(String from, String to, BigDecimal minCapacityTon) {
        List<TruckAvailability> matches = availabilityRepository.search(
                from, to, minCapacityTon == null ? BigDecimal.ZERO : minCapacityTon,
                TruckAvailability.Status.AVAILABLE);

        Map<Long, Truck> trucksById = truckRepository.findAllById(
                matches.stream().map(TruckAvailability::getTruckId).toList()
        ).stream().collect(Collectors.toMap(Truck::getId, t -> t));

        return matches.stream().map(a -> {
            Truck truck = trucksById.get(a.getTruckId());
            return new AvailabilityCandidateResponse(
                    a.getId(), a.getTruckId(), truck != null ? truck.getFleetCompanyId() : null,
                    truck != null ? truck.getTruckNo() : null,
                    truck != null ? truck.getTruckType() : null,
                    truck != null ? truck.getStatus().name() : null,
                    a.getRouteFrom(), a.getRouteTo(), a.getAvailableFrom(), a.getExpectedArrival(),
                    a.getAvailableCapacityTon(),
                    a.getStatus().name()
            );
        }).collect(Collectors.toList());
    }

    /**
     * Capacity Verification: re-checks a specific availability slot at the
     * moment it actually matters (a courier is about to commit to it) rather
     * than trusting whatever was true when the search results were generated
     * — the slot could have been booked by someone else in the meantime.
     */
    public CapacityVerificationResponse verify(Long availabilityId, BigDecimal requiredTon) {
        TruckAvailability availability = availabilityRepository.findById(availabilityId).orElse(null);
        if (availability == null) {
            return new CapacityVerificationResponse(false, null, "Availability slot no longer exists");
        }
        if (availability.getStatus() != TruckAvailability.Status.AVAILABLE) {
            return new CapacityVerificationResponse(false, availability.getAvailableCapacityTon(),
                    "Slot is no longer available (status: " + availability.getStatus() + ")");
        }
        if (requiredTon != null && availability.getAvailableCapacityTon().compareTo(requiredTon) < 0) {
            return new CapacityVerificationResponse(false, availability.getAvailableCapacityTon(),
                    "Not enough capacity: has " + availability.getAvailableCapacityTon() + " ton, needs " + requiredTon);
        }
        return new CapacityVerificationResponse(true, availability.getAvailableCapacityTon(), null);
    }

    /**
     * Called the instant a courier clicks "Accept Match" — verifies capacity
     * one more time and, if it still holds, immediately flips the slot to
     * BOOKED so nobody else can reserve it while the fleet manager decides.
     */
    public TruckAvailability reserve(Long availabilityId, BigDecimal requiredTon) {
        CapacityVerificationResponse verification = verify(availabilityId, requiredTon);
        if (!verification.valid()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, verification.reason());
        }
        TruckAvailability availability = availabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Availability not found"));
        availability.setStatus(TruckAvailability.Status.BOOKED);
        return availabilityRepository.save(availability);
    }

    /** Fleet manager rejected the booking — free the slot back up. */
    public TruckAvailability release(Long availabilityId) {
        TruckAvailability availability = availabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Availability not found"));
        availability.setStatus(TruckAvailability.Status.AVAILABLE);
        return availabilityRepository.save(availability);
    }
}
