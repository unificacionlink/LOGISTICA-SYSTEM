package com.tdem.logistica.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    /** Claim con la sesion del token (DEC-L090: sesion unica). */
    public static final String CLAIM_SESION = "sid";

    public String generar(UUID userId, List<String> roles, UUID sesion) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("roles", roles)
                .claim(CLAIM_SESION, sesion.toString())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public int expirationSeconds() {
        return (int) (expirationMinutes * 60);
    }

    /** La sesion del token; null si es un token anterior a la sesion unica. */
    public static UUID sesion(Claims claims) {
        Object sid = claims.get(CLAIM_SESION);
        try {
            return sid == null ? null : UUID.fromString(sid.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public Optional<Claims> validar(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
