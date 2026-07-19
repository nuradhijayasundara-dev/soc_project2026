package com.backhaulmatch.courier.service;

import com.backhaulmatch.courier.dto.CourierDtos.*;
import com.backhaulmatch.courier.entity.Customer;
import com.backhaulmatch.courier.entity.Receiver;
import com.backhaulmatch.courier.entity.Shipment;
import com.backhaulmatch.courier.entity.ShipmentTracking;
import com.backhaulmatch.courier.repository.ShipmentRepository;
import com.backhaulmatch.courier.repository.ShipmentTrackingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingRepository trackingRepository;
    private final CustomerService customerService;
    private final ReceiverService receiverService;

    // Simple in-memory counter for demo shipment codes (SHP-0001, SHP-0002, ...).
    // Fine for a final-year project; swap for a DB sequence/UUID if you need real concurrency safety.
    private final AtomicLong codeCounter = new AtomicLong(0);

    public List<Shipment> listForCompany(Long courierCompanyId) {
        return shipmentRepository.findByCourierCompanyIdOrderByCreatedAtDesc(courierCompanyId);
    }

    public Shipment getById(Long id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found"));
    }

    public List<ShipmentTracking> getTrackingHistory(Long shipmentId) {
        return trackingRepository.findByShipmentIdOrderByUpdatedAtAsc(shipmentId);
    }

    public Shipment create(Long courierCompanyId, CreateShipmentRequest req) {
        // Resolve or create the customer
        Long customerId = req.customerId();
        if (customerId == null) {
            if (req.newCustomer() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Either customerId or newCustomer must be provided");
            }
            Customer customer = customerService.create(courierCompanyId, req.newCustomer());
            customerId = customer.getId();
        } else {
            customerService.getById(customerId); // validates it exists
        }

        // Create the receiver for this shipment
        Receiver receiver = receiverService.create(req.receiver());

        Shipment shipment = new Shipment();
        shipment.setShipmentCode(nextShipmentCode());
        shipment.setCustomerId(customerId);
        shipment.setReceiverId(receiver.getId());
        shipment.setCourierCompanyId(courierCompanyId);
        shipment.setPickupLocation(req.pickupLocation());
        shipment.setPickupDatetime(req.pickupDatetime());
        shipment.setDestination(req.destination());
        shipment.setDeliveryDeadline(req.deliveryDeadline());
        shipment.setWeightKg(req.weightKg());
        shipment.setDimensions(req.dimensions());
        shipment.setParcelType(req.parcelType());
        shipment.setRemarks(req.remarks());
        shipment.setStatus(Shipment.Status.PENDING);

        Shipment saved = shipmentRepository.save(shipment);
        recordTracking(saved.getId(), "PENDING", req.pickupLocation());
        return saved;
    }

    public Shipment updateStatus(Long shipmentId, StatusUpdateRequest req) {
        Shipment shipment = getById(shipmentId);
        Shipment.Status newStatus;
        try {
            newStatus = Shipment.Status.valueOf(req.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + req.status());
        }
        shipment.setStatus(newStatus);
        Shipment saved = shipmentRepository.save(shipment);
        recordTracking(shipmentId, newStatus.name(), req.location());
        return saved;
    }

    public DashboardSummary getDashboardSummary(Long courierCompanyId) {
        long total = shipmentRepository.countByCourierCompanyId(courierCompanyId);
        long matched = shipmentRepository.countByCourierCompanyIdAndStatus(courierCompanyId, Shipment.Status.MATCHED);
        long inTransit = shipmentRepository.countByCourierCompanyIdAndStatus(courierCompanyId, Shipment.Status.IN_TRANSIT);
        long delivered = shipmentRepository.countByCourierCompanyIdAndStatus(courierCompanyId, Shipment.Status.DELIVERED);
        return new DashboardSummary(total, matched, inTransit, delivered);
    }

    private void recordTracking(Long shipmentId, String status, String location) {
        ShipmentTracking tracking = new ShipmentTracking();
        tracking.setShipmentId(shipmentId);
        tracking.setStatus(status);
        tracking.setLocation(location);
        trackingRepository.save(tracking);
    }

    private String nextShipmentCode() {
        long n = shipmentRepository.count() + codeCounter.incrementAndGet();
        return String.format("SHP-%04d", n);
    }
}
