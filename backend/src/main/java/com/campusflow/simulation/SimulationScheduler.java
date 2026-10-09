package com.campusflow.simulation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="campusflow.simulation-enabled", havingValue="true", matchIfMissing=true)
public class SimulationScheduler {
    private final SimulationService simulation;
    public SimulationScheduler(SimulationService simulation) { this.simulation = simulation; }
    @Scheduled(fixedDelayString="${campusflow.simulation-delay-ms:5000}", initialDelay=5000)
    public void tick() { simulation.tick(); }
}
