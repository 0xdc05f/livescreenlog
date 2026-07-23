package com.livescreenlog.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class LiveScreenLogApplication {

	public static void main(String[] args) {
		SpringApplication.run(LiveScreenLogApplication.class, args);
	}

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

}
