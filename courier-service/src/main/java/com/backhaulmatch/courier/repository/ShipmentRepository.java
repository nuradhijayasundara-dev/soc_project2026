package com.backhaulmatch.courier.repository;

import com.backhaulmatch.courier.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    List<Shipment> findByCourierCompanyIdOrderByCreatedAtDesc(Long courierCompanyId);
    long countByCourierCompanyId(Long courierCompanyId);
    long countByCourierCompanyIdAndStatus(Long courierCompanyId, Shipment.Status status);
}
