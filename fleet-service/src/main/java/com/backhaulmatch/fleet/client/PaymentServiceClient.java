package com.backhaulmatch.fleet.client;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * Direct service-to-service call (not via the Gateway): the Fleet Dashboard
 * assembles its "Revenue (MTD)" card from payment-service, resolved by its
 * Eureka name "PAYMENT-SERVICE" through the @LoadBalanced RestTemplate.
 */
@Component
@RequiredArgsConstructor
public class PaymentServiceClient {

    private final RestTemplate restTemplate;

    private static final String PAYMENT_SERVICE_URL =
            "http://PAYMENT-SERVICE/api/payment/reports/fleet/mtd?fleetCompanyId=";

    /** Sum of this fleet company's PAID invoices created since the 1st of this month. */
    public BigDecimal getMonthToDateRevenue(Long fleetCompanyId) {
        return restTemplate.getForObject(PAYMENT_SERVICE_URL + fleetCompanyId, BigDecimal.class);
    }
}
