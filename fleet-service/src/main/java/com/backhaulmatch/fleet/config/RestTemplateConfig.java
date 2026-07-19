package com.backhaulmatch.fleet.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    /**
     * @LoadBalanced lets us call http://COURIER-SERVICE/... using the Eureka
     * service name instead of a hardcoded host:port — Spring Cloud resolves
     * it to a live instance at request time.
     */
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
