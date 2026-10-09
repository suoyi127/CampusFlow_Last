package com.campusflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;

@SpringBootApplication
@EnableScheduling
public class CampusFlowApplication {
    public static void main(String[] args) { SpringApplication.run(CampusFlowApplication.class, args); }
    @Bean Clock clock() { return Clock.systemUTC(); }
}
