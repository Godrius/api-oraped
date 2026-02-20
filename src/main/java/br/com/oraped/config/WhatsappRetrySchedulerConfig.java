package br.com.oraped.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class WhatsappRetrySchedulerConfig {

    @Bean
    public ThreadPoolTaskScheduler whatsappRetryTaskScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(2);
        s.setThreadNamePrefix("wa-retry-");
        s.setDaemon(true);
        s.initialize();
        return s;
    }
}