package com.livescreenlog.app.config;

import com.livescreenlog.app.domain.User;
import com.livescreenlog.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;

/**
 * Bootstraps the very first SUPER_ADMIN user on startup if the users table is empty.
 * Also supports --create-admin=user:pass:role (or LIVESCREENLOG_CREATE_ADMIN env) to create
 * additional admins (ADMIN default role) even when users exist, if username not present.
 * This provides the "맨 처음 서버에서 슈퍼유저 생성" mechanism + CLI additional admin bootstrap.
 *
 * Configure via:
 *   livescreenlog.security.dashboard-username
 *   livescreenlog.security.dashboard-password
 *   --create-admin=...
 *
 * After first creation, change the password through future admin UI / API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialAdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final LiveScreenLogProperties properties;
    private final PasswordEncoder passwordEncoder;

    private static final List<String> WEAK_PASSWORDS = List.of(
            "admin", "password", "123456", "admin123", "change-me", "changeme", "default"
    );
    private static final String PASSWORD_CHARSET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#%";
    private static final int GENERATED_PASSWORD_LENGTH = 20;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public void run(ApplicationArguments args) {
        long userCount = userRepository.count();
        if (userCount > 0) {
            log.info("Admin users already exist ({}). Env dashboard password is ignored.", userCount);
        } else {
            String username = properties.getDashboardUsername();
            String rawPassword = properties.getDashboardPassword();

            if (username == null || username.isBlank()) {
                username = "admin";
            }
            if (rawPassword == null || rawPassword.isBlank() || isWeak(rawPassword)) {
                rawPassword = generatePassword();
            }

            String hash = passwordEncoder.encode(rawPassword);

            User admin = User.builder()
                    .username(username)
                    .passwordHash(hash)
                    .role("SUPER_ADMIN")
                    .enabled(true)
                    .build();

            userRepository.save(admin);

            log.warn("################################################################");
            log.warn("# FIRST BOOT — copy this password now. It is not shown again.");
            log.warn("# username : {}", username);
            log.warn("# password : {}", rawPassword);
            log.warn("################################################################");
        }

        // CLI / env for additional admin (or even first if properties skipped), create if username not exists
        String createAdminArg = null;
        if (args.getOptionNames().contains("create-admin")) {
            var vals = args.getOptionValues("create-admin");
            if (vals != null && !vals.isEmpty()) {
                createAdminArg = vals.get(0);
            }
        }
        if (createAdminArg == null || createAdminArg.isBlank()) {
            createAdminArg = System.getenv("LIVESCREENLOG_CREATE_ADMIN");
        }
        if (createAdminArg != null && !createAdminArg.isBlank()) {
            String username;
            String rawPassword;
            String role = "ADMIN";
            int first = createAdminArg.indexOf(':');
            if (first <= 0) {
                log.warn("Invalid --create-admin format, expected user:pass[:role]");
                return;
            }
            username = createAdminArg.substring(0, first);
            int second = createAdminArg.indexOf(':', first + 1);
            if (second > first) {
                rawPassword = createAdminArg.substring(first + 1, second);
                role = createAdminArg.substring(second + 1);
            } else {
                rawPassword = createAdminArg.substring(first + 1);
            }
            if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
                log.warn("Invalid --create-admin or LIVESCREENLOG_CREATE_ADMIN: username and password required");
                return;
            }
            if (userRepository.existsByUsername(username)) {
                return;
            }
            if (isWeak(rawPassword)) {
                log.warn("The provided additional admin password looks weak. Please change it immediately after first login.");
            }
            String hash = passwordEncoder.encode(rawPassword);
            try {
                role = UserRoles.requireAllowed(role);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid role '{}' for --create-admin; using ADMIN", role);
                role = "ADMIN";
            }
            User admin = User.builder()
                    .username(username)
                    .passwordHash(hash)
                    .role(role)
                    .enabled(true)
                    .build();
            userRepository.save(admin);
            log.info("Additional admin created via CLI: username='{}' role='{}'", username, role);
        }
    }

    private String generatePassword() {
        String generated;
        do {
            StringBuilder sb = new StringBuilder(GENERATED_PASSWORD_LENGTH);
            for (int i = 0; i < GENERATED_PASSWORD_LENGTH; i++) {
                sb.append(PASSWORD_CHARSET.charAt(SECURE_RANDOM.nextInt(PASSWORD_CHARSET.length())));
            }
            generated = sb.toString();
        } while (isWeak(generated));
        return generated;
    }

    private boolean isWeak(String pwd) {
        String lower = pwd.toLowerCase();
        return WEAK_PASSWORDS.stream().anyMatch(lower::contains) || pwd.length() < 8;
    }
}
