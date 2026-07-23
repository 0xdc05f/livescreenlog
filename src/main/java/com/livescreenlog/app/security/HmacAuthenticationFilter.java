package com.livescreenlog.app.security;

import com.livescreenlog.app.config.LiveScreenLogProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class HmacAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "x-livescreenlog-session-token";
    private final LiveScreenLogProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!(path.equals("/api/events") || path.equals("/api/heartbeat") || path.equals("/api/stop"))) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(HEADER_NAME);
        if (token == null || token.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            String sessionId = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            long expirationMillis = Long.parseLong(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            String signature = parts[2];

            if (System.currentTimeMillis() > expirationMillis) {
                log.warn("Token expired for session: {}", sessionId);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            String expectedSignature = calculateSignature(sessionId, expirationMillis);
            if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
                log.warn("Invalid signature for session: {}", sessionId);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    sessionId, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_SESSION"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("Token validation error", e);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    private String calculateSignature(String sessionId, long expirationMillis) throws NoSuchAlgorithmException, InvalidKeyException {
        String payload = sessionId + ":" + expirationMillis;
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(properties.getHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }
}
