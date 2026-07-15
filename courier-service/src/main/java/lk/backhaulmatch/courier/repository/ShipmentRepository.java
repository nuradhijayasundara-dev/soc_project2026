package lk.backhaulmatch.courier.repository;

import lk.backhaulmatch.courier.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    List<Shipment> findByCourierCompany_CourierCompanyId(Integer courierCompanyId);
    List<Shipment> findByStatus(Shipment.Status status);
}
