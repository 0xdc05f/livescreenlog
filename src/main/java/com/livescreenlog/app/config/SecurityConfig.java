package com.livescreenlog.app.config;

import com.livescreenlog.app.security.HmacAuthenticationFilter;
import com.livescreenlog.app.service.ServerConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final HmacAuthenticationFilter hmacAuthenticationFilter;
    private final LiveScreenLogProperties properties;
    private final ServerConfigService serverConfigService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        boolean dashboard = properties.isDashboardEnabled();

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers(
                                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/sessions"),
                                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/events"),
                                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/heartbeat"),
                                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/stop")
                        )
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> {
                                    String uri = request.getRequestURI();
                                    return uri.startsWith("/api/") && !uri.startsWith("/api/push/connect");
                                }
                        )
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                request -> true
                        )
                        .accessDeniedHandler((req, res, ex) -> {
                            if (res.isCommitted()) return;
                            if (req.getRequestURI().startsWith("/api/")) {
                                res.setStatus(403);
                                res.setContentType("application/json");
                                res.getWriter().write("{\"error\":\"forbidden\"}");
                                return;
                            }
                            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                                res.sendRedirect("/");
                                return;
                            }
                            res.sendRedirect("/login");
                        })
                )
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/login", "/login.html", "/logout", "/error", "/favicon.svg", "/favicon.ico",
                                    "/actuator/health", "/actuator/info", "/sample*.html", "/livescreenlog.js",
                                    "/**/*.css", "/**/*.js").permitAll()
                            .requestMatchers("/api/events", "/api/heartbeat", "/api/stop").hasRole("SESSION")
                            .requestMatchers(HttpMethod.GET, "/api/push/connect").permitAll()
                            .requestMatchers("/api/push/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                            .requestMatchers("/api/admin/**").hasRole("SUPER_ADMIN")
                            .requestMatchers(HttpMethod.GET, "/api/projects/**").hasAnyRole("SUPER_ADMIN", "ADMIN", "VIEWER")
                            .requestMatchers("/api/projects/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                            .requestMatchers(HttpMethod.POST, "/api/sessions").permitAll();
                    if (!dashboard) {
                        auth.requestMatchers("/", "/index.html", "/change-password", "/change-password.html",
                                        "/api/sessions/**", "/api/config/**", "/api/stats/**", "/api/me", "/api/me/**")
                                .denyAll();
                    }
                    auth.requestMatchers(HttpMethod.GET, "/api/stats/**").hasAnyRole("SUPER_ADMIN", "ADMIN", "VIEWER")
                            .requestMatchers(HttpMethod.GET, "/api/sessions/**").hasAnyRole("SUPER_ADMIN", "ADMIN", "VIEWER")
                            .requestMatchers("/api/sessions/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                            .requestMatchers("/api/config/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                            .requestMatchers("/", "/index.html", "/change-password", "/change-password.html")
                                .hasAnyRole("SUPER_ADMIN", "ADMIN", "VIEWER")
                            .requestMatchers("/api/me", "/api/me/**").authenticated()
                            .anyRequest().authenticated();
                })
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
                .addFilterBefore(hmacAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            CorsConfiguration configuration = new CorsConfiguration();
            List<String> allowedOrigins;
            try {
                allowedOrigins = serverConfigService.getEffectiveAllowedCaptureOrigins();
            } catch (Exception ignored) {
                allowedOrigins = properties.getAllowedCaptureOrigins();
            }
            if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
                configuration.setAllowedOrigins(allowedOrigins);
            } else {
                allowedOrigins = List.of("*");
                configuration.setAllowedOrigins(allowedOrigins);
            }
            configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            configuration.setAllowedHeaders(List.of("*"));
            boolean wildcard = allowedOrigins.contains("*");
            configuration.setAllowCredentials(!wildcard);
            return configuration;
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
