package lk.backhaulmatch.courier.repository;

import lk.backhaulmatch.courier.entity.ShipmentTracking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShipmentTrackingRepository extends JpaRepository<ShipmentTracking, Long> {
    List<ShipmentTracking> findByShipment_ShipmentIdOrderByUpdatedAtDesc(Long shipmentId);
}
