package com.backhaulmatch.matching.controller;

import com.backhaulmatch.matching.util.DistanceCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Local geocoder exposed to the portals — the same Sri Lanka city table the
 * matching engine's {@link DistanceCalculator} resolves names with, served
 * through the API Gateway so the frontends never call an external geocoding
 * service. Routing geometry/distance stays entirely with the local OSRM engine.
 */
@RestController
@RequestMapping("/api/matching/geocode")
@RequiredArgsConstructor
public class GeocodeController {

    private final DistanceCalculator distanceCalculator;

    // LocationPicker autocomplete: "q=Kandy" -> the matching city row.
    @GetMapping("/search")
    public ResponseEntity<List<DistanceCalculator.GeoLocation>> search(@RequestParam String q,
                                                                        @RequestParam(defaultValue = "6") int limit) {
        return ResponseEntity.ok(distanceCalculator.searchLocations(q, limit));
    }

    // LocationPicker map-click reverse lookup: coordinates -> nearest city name.
    @GetMapping("/reverse")
    public ResponseEntity<DistanceCalculator.GeoLocation> reverse(@RequestParam double lat,
                                                                   @RequestParam double lng) {
        return ResponseEntity.ok(distanceCalculator.reverseGeocode(lat, lng));
    }
}