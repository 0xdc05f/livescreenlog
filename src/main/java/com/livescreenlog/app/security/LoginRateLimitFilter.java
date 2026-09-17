package com.livescreenlog.app.security;

import com.livescreenlog.app.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
        }
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !"/login".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        String ip = request.getRemoteAddr();
        String username = request.getParameter("username");
        if (username == null) {
            username = "";
        }
        if (!rateLimitService.allowLogin(username, ip)) {
            response.setStatus(429);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
