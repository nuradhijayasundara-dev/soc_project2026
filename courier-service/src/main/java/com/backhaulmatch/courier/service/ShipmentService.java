package com.backhaulmatch.courier.service;

import com.backhaulmatch.courier.client.MatchingServiceClient;
import com.backhaulmatch.courier.client.NotificationClient;
import com.backhaulmatch.courier.client.PaymentServiceClient;
import com.backhaulmatch.courier.dto.CourierDtos.*;
import com.backhaulmatch.courier.entity.Customer;
import com.backhaulmatch.courier.entity.CourierCompany;
import com.backhaulmatch.courier.entity.Receiver;
import com.backhaulmatch.courier.entity.Shipment;
import com.backhaulmatch.courier.entity.ShipmentTracking;
import com.backhaulmatch.courier.repository.CourierCompanyRepository;
import com.backhaulmatch.courier.repository.ShipmentRepository;
import com.backhaulmatch.courier.repository.ShipmentTrackingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingRepository trackingRepository;
    private final CustomerService customerService;
    private final ReceiverService receiverService;
    private final CourierCompanyRepository courierCompanyRepository;
    private final NotificationClient notificationClient;
    private final MatchingServiceClient matchingServiceClient;
    private final PaymentServiceClient paymentServiceClient;

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
        shipment.setPickupName(req.pickupName());
        shipment.setPickupLatitude(req.pickupLatitude());
        shipment.setPickupLongitude(req.pickupLongitude());
        shipment.setPickupDatetime(req.pickupDatetime());
        shipment.setDestination(req.destination());
        shipment.setDestinationName(req.destinationName());
        shipment.setDestinationLatitude(req.destinationLatitude());
        shipment.setDestinationLongitude(req.destinationLongitude());
        shipment.setDistanceKm(req.distanceKm());
        shipment.setEstimatedDuration(req.estimatedDuration());
        shipment.setDeliveryDeadline(req.deliveryDeadline());
        shipment.setWeightKg(req.weightKg());
        shipment.setDimensions(req.dimensions());
        shipment.setParcelType(req.parcelType());
        shipment.setRequiredVehicleType(req.requiredVehicleType());
        shipment.setPriority(req.priority());
        shipment.setRemarks(req.remarks());
        shipment.setStatus(Shipment.Status.PENDING);

        // Show the courier a number before they commit: the authoritative
        // estimate comes from payment-service, the same tariff the invoice
        // will use later. Best-effort — a missing estimate doesn't block creation.
        Double distance = req.distanceKm() == null ? null : req.distanceKm().doubleValue();
        shipment.setEstimatedCost(paymentServiceClient.estimatePrice(
                distance, req.weightKg(), req.requiredVehicleType(), req.priority()));

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

        courierCompanyRepository.findById(shipment.getCourierCompanyId()).ifPresent(company ->
                notificationClient.notify(
                        company.getUserId(),
                        "SHIPMENT_STATUS",
                        "Shipment " + shipment.getShipmentCode() + " is now " + newStatus.name(),
                        req.location() != null
                                ? "Last seen at " + req.location() + "."
                                : "Status updated.",
                        shipment.getId()
                )
        );

        return saved;
    }

    public DashboardSummary getDashboardSummary(Long courierCompanyId) {
        long total = shipmentRepository.countByCourierCompanyId(courierCompanyId);
        long matched = shipmentRepository.countByCourierCompanyIdAndStatus(courierCompanyId, Shipment.Status.MATCHED)
                + shipmentRepository.countByCourierCompanyIdAndStatus(courierCompanyId, Shipment.Status.MATCH_FOUND)
                + shipmentRepository.countByCourierCompanyIdAndStatus(courierCompanyId, Shipment.Status.BOOKING_PENDING)
                + shipmentRepository.countByCourierCompanyIdAndStatus(courierCompanyId, Shipment.Status.CONFIRMED)
                + shipmentRepository.countByCourierCompanyIdAndStatus(courierCompanyId, Shipment.Status.DRIVER_ASSIGNED);
        long inTransit = shipmentRepository.countByCourierCompanyIdAndStatus(courierCompanyId, Shipment.Status.IN_TRANSIT);
        long delivered = shipmentRepository.countByCourierCompanyIdAndStatus(courierCompanyId, Shipment.Status.DELIVERED);
        return new DashboardSummary(total, matched, inTransit, delivered);
    }

    /**
     * "Courier Reports": total shipments / delivered / cancelled are this
     * service's own SQL COUNT queries; successful matches and cost savings
     * are fetched live from matching-service and payment-service (each
     * computed there the same way — SQL aggregates, no shared report table).
     */
    public CourierReportSummary getReportSummary(Long courierUserId, Long courierCompanyId) {
        long total = shipmentRepository.countByCourierCompanyId(courierCompanyId);
        long delivered = shipmentRepository.countByCourierCompanyIdAndStatus(courierCompanyId, Shipment.Status.DELIVERED);
        long cancelled = shipmentRepository.countByCourierCompanyIdAndStatus(courierCompanyId, Shipment.Status.CANCELLED);

        long successfulMatches = matchingServiceClient.getSuccessfulMatchCount(courierUserId);
        var costSavings = paymentServiceClient.getCostSavings(courierUserId);

        return new CourierReportSummary(total, delivered, cancelled, successfulMatches, costSavings);
    }

    private void recordTracking(Long shipmentId, String status, String location) {
        ShipmentTracking tracking = new ShipmentTracking();
        tracking.setShipmentId(shipmentId);
        tracking.setStatus(status);
        tracking.setLocation(location);
        trackingRepository.save(tracking);
    }

    private String nextShipmentCode() {
        // Codes are taken from the DB so we never collide with rows that were
        // seeded or created earlier (count()-based counters drift once codes
        // are deleted or pre-seeded).
        Set<String> used = new HashSet<>();
        for (Shipment s : shipmentRepository.findAll()) {
            used.add(s.getShipmentCode());
        }
        long n = used.size() + codeCounter.incrementAndGet();
        while (used.contains(String.format("SHP-%04d", n))) {
            n++;
        }
        return String.format("SHP-%04d", n);
    }
}
