package com.livescreenlog.app.controller;

import com.livescreenlog.app.domain.User;
import com.livescreenlog.app.dto.PasswordChangeRequest;
import com.livescreenlog.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class CurrentUserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/api/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
      if (authentication == null) return ResponseEntity.status(401).build();
      return ResponseEntity.ok(Map.of(
        "username", authentication.getName(),
        "authorities", authentication.getAuthorities().stream().map(a->a.getAuthority()).toList(),
        "isAdmin", authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().contains("ADMIN"))
      ));
    }

    @PostMapping("/api/me/password")
    public ResponseEntity<?> changePassword(Authentication authentication, @RequestBody PasswordChangeRequest req) {
        if (authentication == null) return ResponseEntity.status(401).build();
        String currentPassword = req == null ? null : req.currentPassword();
        String newPassword = req == null ? null : req.newPassword();
        if (currentPassword == null || currentPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "currentPassword and newPassword are required"));
        }
        if (newPassword.length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "newPassword must be at least 8 characters"));
        }
        if (newPassword.equals(currentPassword)) {
            return ResponseEntity.badRequest().body(Map.of("error", "newPassword must differ from currentPassword"));
        }
        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of("error", "current password is incorrect"));
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}