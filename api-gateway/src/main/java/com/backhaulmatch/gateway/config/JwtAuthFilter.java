package com.backhaulmatch.gateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Gateway-level filter: validates the JWT on every request to a protected route
 * and forwards the userId / role as headers to downstream services.
 * Public paths (login/register) are excluded in application.yml route config.
 */
@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    // NOTE: for the demo this is a static shared secret; in production pull this
    // from an env var / secrets manager and keep it identical to auth-service's secret.
    private static final String SECRET = "backhaul-match-super-secret-key-change-me-1234567890";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    public JwtAuthFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            List<String> authHeaders = exchange.getRequest().getHeaders().get("Authorization");
            if (authHeaders == null || authHeaders.isEmpty() || !authHeaders.get(0).startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            String token = authHeaders.get(0).substring(7);
            try {
                Claims claims = Jwts.parser().verifyWith(KEY).build()
                        .parseSignedClaims(token).getPayload();

                exchange = exchange.mutate().request(r -> r
                        .header("X-User-Id", claims.getSubject())
                        .header("X-User-Role", String.valueOf(claims.get("role")))
                ).build();
            } catch (Exception e) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        };
    }

    public static class Config {
        // marker class required by AbstractGatewayFilterFactory; no extra fields needed yet.
    }
}
