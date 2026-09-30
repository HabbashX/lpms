package com.larv.pharmacy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Injectable clock so report periods ("today") and token issue times are
 * deterministic and testable.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(AppProperties appProperties) {
        String zone = appProperties.timezone();
        ZoneId zoneId = (zone == null || zone.isBlank()) ? ZoneId.of("UTC") : ZoneId.of(zone);
        return Clock.system(zoneId);
    }
}
