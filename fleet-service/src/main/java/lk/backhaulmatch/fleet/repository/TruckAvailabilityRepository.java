package lk.backhaulmatch.fleet.repository;

import lk.backhaulmatch.fleet.entity.TruckAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TruckAvailabilityRepository extends JpaRepository<TruckAvailability, Long> {
    List<TruckAvailability> findByRouteFromCityIdAndRouteToCityIdAndStatus(
            Integer fromCityId, Integer toCityId, TruckAvailability.Status status);
}
