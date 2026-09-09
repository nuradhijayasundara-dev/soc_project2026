package com.backhaulmatch.admin.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        // JdkClientHttpRequestFactory supports PATCH, which the company
        // approval flow uses (courier/fleet-service /approve and /reject).
        return new RestTemplate(new JdkClientHttpRequestFactory());
    }
}
