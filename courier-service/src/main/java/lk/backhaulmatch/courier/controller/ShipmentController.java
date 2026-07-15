package lk.backhaulmatch.courier.controller;

import lk.backhaulmatch.courier.entity.Shipment;
import lk.backhaulmatch.courier.repository.ShipmentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {

    private final ShipmentRepository shipmentRepository;

    public ShipmentController(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    @GetMapping
    public List<Shipment> getAll() {
        return shipmentRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Shipment> getById(@PathVariable Long id) {
        return shipmentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Shipment create(@RequestBody Shipment shipment) {
        return shipmentRepository.save(shipment);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Shipment> updateStatus(@PathVariable Long id, @RequestParam Shipment.Status status) {
        return shipmentRepository.findById(id).map(s -> {
            s.setStatus(status);
            return ResponseEntity.ok(shipmentRepository.save(s));
        }).orElse(ResponseEntity.notFound().build());
    }
}
