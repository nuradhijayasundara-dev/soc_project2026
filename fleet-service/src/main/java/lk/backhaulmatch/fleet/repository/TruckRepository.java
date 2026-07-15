package lk.backhaulmatch.fleet.repository;

import lk.backhaulmatch.fleet.entity.Truck;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TruckRepository extends JpaRepository<Truck, Long> {
}
