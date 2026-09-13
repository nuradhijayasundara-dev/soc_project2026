package com.backhaulmatch.gps.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    /**
     * @LoadBalanced lets us call http://FLEET-SERVICE/... and
     * http://COURIER-SERVICE/... using Eureka service names instead of
     * hardcoded host:port — Spring Cloud resolves them at request time.
     */
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
