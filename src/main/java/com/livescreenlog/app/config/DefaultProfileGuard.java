package com.livescreenlog.app.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultProfileGuard implements ApplicationRunner {

    private final Environment environment;
    private final LiveScreenLogProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        String[] active = environment.getActiveProfiles();
        String propertyProfile = environment.getProperty("spring.profiles.active");
        String envProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        boolean noProfile = (active == null || active.length == 0)
                && (propertyProfile == null || propertyProfile.isBlank())
                && (envProfile == null || envProfile.isBlank());
        if (!noProfile) {
            return;
        }
        String hmac = properties.getHmacSecret();
        if (hmac == null || hmac.length() < 32 || hmac.contains("default-hmac-secret")) {
            log.warn("Running without prod profile; set SPRING_PROFILES_ACTIVE=prod or dev");
        }
    }
}
