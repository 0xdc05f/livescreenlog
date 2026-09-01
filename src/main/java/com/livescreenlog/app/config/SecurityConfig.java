package com.livescreenlog.app.config;

import com.livescreenlog.app.security.HmacAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final HmacAuthenticationFilter hmacAuthenticationFilter;
    private final LiveScreenLogProperties properties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getRequestURI().startsWith("/api/") && !request.getRequestURI().startsWith("/api/push/")
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
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/login.html", "/logout", "/error", "/favicon.svg", "/favicon.ico", "/actuator/health", "/actuator/info", "/sample*.html", "/livescreenlog.js", "/**/*.css", "/**/*.js").permitAll()
                        .requestMatchers("/api/events", "/api/heartbeat", "/api/stop").hasRole("SESSION")
                        .requestMatchers("/api/push/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/push/connect").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/projects/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/sessions").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/stats/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                        .requestMatchers("/api/sessions/**", "/api/config/**", "/", "/index.html", "/change-password", "/change-password.html").hasAnyRole("SUPER_ADMIN", "ADMIN")
                        .requestMatchers("/api/me", "/api/me/**").authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
                .logout(logout -> logout .logoutSuccessUrl("/login?logout") .permitAll() )
                .addFilterBefore(hmacAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> allowedOrigins = properties.getAllowedCaptureOrigins();
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            configuration.setAllowedOrigins(allowedOrigins);
        } else {
            configuration.setAllowedOrigins(List.of("*"));
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Include Content-Encoding so browser SDK can send gzip event batches cross-origin
        configuration.setAllowedHeaders(List.of("*"));

        if (allowedOrigins == null || allowedOrigins.contains("*")) {
            configuration.setAllowCredentials(false);
        } else {
            configuration.setAllowCredentials(true);
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
