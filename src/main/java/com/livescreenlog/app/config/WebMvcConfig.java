package com.livescreenlog.app.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig {

    private final LiveScreenLogProperties properties;

    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer() {
        return resolver -> resolver.setMaxPageSize(Math.max(1, properties.getMaxSearchPageSize()));
    }
}
