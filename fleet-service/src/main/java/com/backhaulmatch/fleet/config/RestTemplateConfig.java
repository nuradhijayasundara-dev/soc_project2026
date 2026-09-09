package com.backhaulmatch.fleet.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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
        // The default request factory (HttpURLConnection-based) can't send PATCH
        // requests — throws "Invalid HTTP method: PATCH" at runtime. This service
        // calls courier-service's PATCH /shipments/{id}/status endpoint when a
        // driver starts a trip, so it needs a factory that actually supports PATCH.
        return new RestTemplate(new JdkClientHttpRequestFactory());
    }
}
