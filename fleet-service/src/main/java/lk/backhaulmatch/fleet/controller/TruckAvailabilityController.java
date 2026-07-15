package lk.backhaulmatch.fleet.controller;

import lk.backhaulmatch.fleet.entity.TruckAvailability;
import lk.backhaulmatch.fleet.repository.TruckAvailabilityRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fleet/availability")
public class TruckAvailabilityController {

    private final TruckAvailabilityRepository availabilityRepository;

    public TruckAvailabilityController(TruckAvailabilityRepository availabilityRepository) {
        this.availabilityRepository = availabilityRepository;
    }

    @GetMapping
    public List<TruckAvailability> getAll() {
        return availabilityRepository.findAll();
    }

    @GetMapping("/search")
    public List<TruckAvailability> search(@RequestParam Integer fromCityId, @RequestParam Integer toCityId) {
        return availabilityRepository.findByRouteFromCityIdAndRouteToCityIdAndStatus(
                fromCityId, toCityId, TruckAvailability.Status.AVAILABLE);
    }

    @PostMapping
    public TruckAvailability create(@RequestBody TruckAvailability availability) {
        return availabilityRepository.save(availability);
    }
}
