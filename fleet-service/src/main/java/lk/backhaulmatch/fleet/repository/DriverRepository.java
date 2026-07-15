package lk.backhaulmatch.fleet.repository;

import lk.backhaulmatch.fleet.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, Long> {
}
