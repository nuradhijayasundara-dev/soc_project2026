package com.backhaulmatch.fleet.service;

import com.backhaulmatch.fleet.client.CourierServiceClient;
import com.backhaulmatch.fleet.dto.TripDtos.AssignDriverRequest;
import com.backhaulmatch.fleet.dto.TripDtos.CreateTripRequest;
import com.backhaulmatch.fleet.entity.Driver;
import com.backhaulmatch.fleet.entity.Trip;
import com.backhaulmatch.fleet.entity.Truck;
import com.backhaulmatch.fleet.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final TruckService truckService;
    private final DriverService driverService;
    private final CourierServiceClient courierServiceClient;

    public List<Trip> listForTrucks(List<Long> truckIds) {
        return tripRepository.findByTruckIdInOrderByStartTimeDesc(truckIds);
    }

    public Trip getById(Long id) {
        return tripRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
    }

    /**
     * Creates a Trip for a truck + driver, optionally against a real shipment.
     * If a shipmentId is supplied we validate it exists by calling courier-service
     * directly (service-to-service communication, bypassing the Gateway).
     */
    public Trip create(CreateTripRequest req) {
        Truck truck = truckService.getById(req.truckId());
        Driver driver = driverService.getById(req.driverId());

        if (truck.getStatus() != Truck.Status.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Truck is not available");
        }
        if (driver.getStatus() != Driver.Status.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Driver is not available");
        }
        if (req.shipmentId() != null) {
            courierServiceClient.getShipment(req.shipmentId()); // throws 400 if it doesn't exist
        }

        Trip trip = new Trip();
        trip.setTruckId(req.truckId());
        trip.setDriverId(req.driverId());
        trip.setShipmentId(req.shipmentId());
        trip.setStartTime(LocalDateTime.now());
        trip.setStatus(Trip.Status.SCHEDULED);
        Trip saved = tripRepository.save(trip);

        truck.setStatus(Truck.Status.ON_TRIP);
        driver.setStatus(Driver.Status.ON_TRIP);

        return saved;
    }

    /** "Driver assignment" — reassign a different driver to an existing (not-yet-started) trip. */
    public Trip assignDriver(Long tripId, AssignDriverRequest req) {
        Trip trip = getById(tripId);
        if (trip.getStatus() != Trip.Status.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Can only reassign a driver before the trip starts");
        }
        Driver newDriver = driverService.getById(req.driverId());
        if (newDriver.getStatus() != Driver.Status.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Driver is not available");
        }

        driverService.setStatus(trip.getDriverId(), Driver.Status.AVAILABLE); // free the old driver
        trip.setDriverId(newDriver.getId());
        driverService.setStatus(newDriver.getId(), Driver.Status.ON_TRIP);

        return tripRepository.save(trip);
    }
}
