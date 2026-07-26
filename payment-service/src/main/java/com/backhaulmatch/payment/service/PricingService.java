package com.backhaulmatch.payment.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The authoritative cost calculation for a booking — separate from the quick
 * estimate matching-service shows at match time, which exists only so the
 * courier has a number to look at before committing. This is what actually
 * gets invoiced, and it's the one place pricing logic lives, so it can be
 * swapped for a real rate table / fuel-surcharge model later without hunting
 * through other services.
 */
@Service
public class PricingService {

    // Placeholder tariff until a real Pricing rate table exists.
    private static final BigDecimal BASE_FARE = new BigDecimal("1000");       // LKR, flat
    private static final BigDecimal RATE_PER_KM = new BigDecimal("150");      // LKR per km
    private static final BigDecimal RATE_PER_TON = new BigDecimal("300");     // LKR per ton, on top of distance
    private static final double DEFAULT_DISTANCE_KM = 150.0; // used if distanceKm wasn't supplied

    /**
     * Cost Calculation: base fare + distance charge + weight charge.
     * distanceKm and weightKg are both optional inputs (a caller might not
     * have one or the other) — missing values fall back to sane defaults
     * rather than producing a zero or null invoice amount.
     */
    public BigDecimal calculateCost(Double distanceKm, BigDecimal weightKg) {
        double km = (distanceKm == null || distanceKm <= 0) ? DEFAULT_DISTANCE_KM : distanceKm;
        BigDecimal distanceCharge = BigDecimal.valueOf(km).multiply(RATE_PER_KM);

        BigDecimal tons = weightKg == null
                ? BigDecimal.ZERO
                : weightKg.divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP);
        BigDecimal weightCharge = tons.multiply(RATE_PER_TON);

        return BASE_FARE.add(distanceCharge).add(weightCharge).setScale(2, RoundingMode.HALF_UP);
    }
}
