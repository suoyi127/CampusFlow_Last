package com.campusflow.simulation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.campusflow.system.ConfigService;
import java.time.*;

@Component
@ConditionalOnProperty(name="campusflow.simulation-enabled", havingValue="true", matchIfMissing=true)
public class SimulationScheduler {
    private final SimulationService simulation;
    private final ConfigService configs;
    private final Clock clock;
    private Instant lastTick;
    public SimulationScheduler(SimulationService simulation,ConfigService configs,Clock clock) {
        this.simulation=simulation;this.configs=configs;this.clock=clock;this.lastTick=clock.instant();
    }
    @Scheduled(fixedDelay=1000, initialDelay=1000)
    public synchronized void tick() {
        var now=clock.instant();
        // 固定轻量检查节拍读取持久化周期，配置更新无需重启或重复注册定时任务。
        if (Duration.between(lastTick,now).compareTo(Duration.ofSeconds(configs.current().simulationSeconds()))<0) return;
        simulation.tick();lastTick=now;
    }
}
