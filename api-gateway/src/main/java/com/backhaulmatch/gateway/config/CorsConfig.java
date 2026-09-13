package com.backhaulmatch.gateway.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class CorsConfig {

    // CORS is handled by Spring Cloud Gateway's globalcors in application.yml,
    // which runs before Spring Security and correctly intercepts OPTIONS preflight
    // requests. This class is intentionally empty — do NOT add a CorsWebFilter bean
    // here, as it would run AFTER Spring Security and fail to add CORS headers.
}
