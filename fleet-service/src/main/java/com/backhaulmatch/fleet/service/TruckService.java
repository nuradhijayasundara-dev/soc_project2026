package com.backhaulmatch.fleet.service;

import com.backhaulmatch.fleet.client.MatchingServiceClient;
import com.backhaulmatch.fleet.dto.FleetDtos.AvailabilityRequest;
import com.backhaulmatch.fleet.dto.FleetDtos.TruckRequest;
import com.backhaulmatch.fleet.entity.Truck;
import com.backhaulmatch.fleet.entity.TruckAvailability;
import com.backhaulmatch.fleet.repository.TruckAvailabilityRepository;
import com.backhaulmatch.fleet.repository.TruckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TruckService {

    private final TruckRepository truckRepository;
    private final TruckAvailabilityRepository availabilityRepository;
    private final MatchingServiceClient matchingServiceClient;

    public List<Truck> listForCompany(Long fleetCompanyId) {
        return truckRepository.findByFleetCompanyId(fleetCompanyId);
    }

    public Truck getById(Long id) {
        return truckRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Truck not found"));
    }

    public Truck register(Long fleetCompanyId, TruckRequest req) {
        if (truckRepository.existsByTruckNo(req.truckNo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Truck number already registered");
        }
        Truck truck = new Truck();
        truck.setFleetCompanyId(fleetCompanyId);
        truck.setTruckNo(req.truckNo());
        truck.setCapacityTon(req.capacityTon());
        truck.setTruckType(req.truckType());
        truck.setStatus(Truck.Status.AVAILABLE);
        return truckRepository.save(truck);
    }

    /** "Truck details" screen: truck info + its availability list. */
    public List<TruckAvailability> getAvailability(Long truckId) {
        getById(truckId); // 404 if truck doesn't exist
        return availabilityRepository.findByTruckId(truckId);
    }

    /** "Available capacity input" screen: fleet manager posts a new lane + capacity + date. */
    public TruckAvailability addAvailability(Long truckId, AvailabilityRequest req) {
        getById(truckId);
        TruckAvailability availability = new TruckAvailability();
        availability.setTruckId(truckId);
        availability.setRouteFrom(req.routeFrom());
        availability.setRouteTo(req.routeTo());
        availability.setAvailableFrom(req.availableFrom());
        availability.setAvailableCapacityTon(req.availableCapacityTon());
        availability.setTripType(req.tripType() != null
                ? TruckAvailability.TripType.valueOf(req.tripType().toUpperCase())
                : TruckAvailability.TripType.BACKHAUL);
        availability.setStatus(TruckAvailability.Status.AVAILABLE);
        TruckAvailability saved = availabilityRepository.save(availability);

        // A new backhaul slot is up — ask matching-service to reconsider every
        // WAITING_FOR_MATCH shipment right now, exactly like the quick-add flow
        // (the scheduled rechecker is the fallback if this push is ever missed).
        matchingServiceClient.triggerWaitingRecheck();

        return saved;
    }
}
