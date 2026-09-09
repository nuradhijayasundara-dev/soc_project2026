package com.backhaulmatch.gateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * API-logging filter: records every request that passes through the Gateway
 * (method, path, status, duration, user when authenticated) by posting it to
 * admin-service's internal audit endpoint. The post is async + fire-and-forget
 * with a short timeout, so audit logging can never slow down or fail the
 * request itself — if admin-service is down, the entry is simply lost.
 */
@Component
public class ApiAuditFilter implements GlobalFilter, Ordered {

    private static final String SECRET = "backhaul-match-super-secret-key-change-me-1234567890";
    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private static final String AUDIT_PATH = "/api/admin/audit/internal";

    private final WebClient webClient;

    public ApiAuditFilter(ReactorLoadBalancerExchangeFilterFunction loadBalancerFilter) {
        this.webClient = WebClient.builder()
                .baseUrl("http://ADMIN-SERVICE")
                .filter(loadBalancerFilter)
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (request.getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }
        String path = request.getPath().value();
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        Long userId = parseUserId(request);
        long start = System.currentTimeMillis();
        return chain.filter(exchange).then(Mono.fromRunnable(() -> record(exchange, start, userId)));
    }

    private Long parseUserId(ServerHttpRequest request) {
        String auth = request.getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) return null;
        try {
            Claims claims = Jwts.parser().verifyWith(KEY).build()
                    .parseSignedClaims(auth.substring(7)).getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    private void record(ServerWebExchange exchange, long start, Long userId) {
        try {
            ServerHttpRequest request = exchange.getRequest();
            long duration = System.currentTimeMillis() - start;
            int status = exchange.getResponse().getStatusCode() == null
                    ? 0 : exchange.getResponse().getStatusCode().value();
            String path = request.getPath().value();

            Map<String, Object> body = new HashMap<>();
            body.put("type", "API_CALL");
            body.put("userId", userId);
            body.put("username", userId == null ? null : String.valueOf(userId));
            body.put("action", "HTTP " + request.getMethod());
            body.put("details", String.format("%s %s -> %d (%d ms)", request.getMethod(), path, status, duration));
            body.put("source", "gateway");

            webClient.post()
                    .uri(AUDIT_PATH)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofMillis(3000))
                    .onErrorResume(e -> Mono.empty())
                    .subscribe();
        } catch (Exception ignored) {
            // never let audit logging affect the request that just completed
        }
    }

    /** Runs near the end of the chain so the response status is final when recorded. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
