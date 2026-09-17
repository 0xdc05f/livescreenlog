package com.livescreenlog.app.security;

import com.livescreenlog.app.config.LiveScreenLogProperties;
import com.livescreenlog.app.domain.SessionMetadata;
import com.livescreenlog.app.repository.SessionMetadataRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
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
    private final SessionMetadataRepository sessionMetadataRepository;

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
            if (parts[0].isEmpty() || parts[0].length() > 512 || parts[1].isEmpty() || parts[1].length() > 64) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            String sessionId = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            long expirationMillis = Long.parseLong(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            String signature = parts[2];

            String expectedSignature = calculateSignature(sessionId, expirationMillis);
            if (System.currentTimeMillis() > expirationMillis) {
                log.warn("token expired");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
                log.warn("invalid signature");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            sessionId = sessionId.replaceAll("[\\r\\n\\t]", "");
            SessionMetadata metadata = sessionMetadataRepository.findById(sessionId).orElse(null);
            if (metadata == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            boolean active = "ACTIVE".equals(metadata.getStatus());
            if (!active && !path.equals("/api/stop")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    sessionId, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_SESSION"))
            );
            SecurityContext previous = SecurityContextHolder.getContext();
            try {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
                filterChain.doFilter(request, response);
            } finally {
                SecurityContextHolder.setContext(previous);
            }

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
