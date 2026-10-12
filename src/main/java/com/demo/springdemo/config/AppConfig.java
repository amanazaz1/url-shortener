package com.demo.springdemo.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** Injectable clock so expiry logic is deterministic in tests. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
