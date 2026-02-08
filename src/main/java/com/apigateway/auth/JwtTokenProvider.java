package com.apigateway.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Token provider and validator
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpirationMs; // Default 24 hours

    public JwtTokenProvider(@Value("${jwt.secret:your-super-secret-key-change-this-in-production}") String secret) {
        this.jwtSecret = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(String subject, Map<String, Object> claims) {
        try {
            return Jwts.builder()
                    .setClaims(claims)
                    .setSubject(subject)
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                    .signWith(jwtSecret, SignatureAlgorithm.HS512)
                    .compact();
        } catch (Exception e) {
            log.error("Error generating JWT token", e);
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    public String generateToken(String subject) {
        return generateToken(subject, new HashMap<>());
    }

    public Claims validateAndGetClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(jwtSecret)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("Error validating JWT token: {}", e.getMessage());
            throw new RuntimeException("Invalid JWT token", e);
        }
    }

    public String getSubjectFromToken(String token) {
        try {
            return validateAndGetClaims(token).getSubject();
        } catch (Exception e) {
            log.error("Error extracting subject from token", e);
            return null;
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            return validateAndGetClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}
