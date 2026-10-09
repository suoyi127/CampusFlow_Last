package com.campusflow.system;

import com.campusflow.auth.AccountService;
import com.campusflow.space.SpaceService;
import com.campusflow.simulation.SimulationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final AccountService accounts;
    private final SpaceService spaces;
    private final SimulationService simulation;
    private final Instant startedAt;
    public SystemController(AccountService accounts, SpaceService spaces, SimulationService simulation, Clock clock) {
        this.accounts = accounts; this.spaces = spaces; this.simulation = simulation; this.startedAt = clock.instant();
    }
    @GetMapping("/overview") Map<String,Object> overview() {
        return Map.of("version","0.1.0", "startedAt",startedAt,"accountCount",accounts.count(),"spaceCount",spaces.all().size(),
            "simulationRunId",simulation.runId(),"running",simulation.running(),"pollSeconds",5,"validitySeconds",30,"noiseWindowSeconds",60);
    }
    @PostMapping("/simulation/{action}") Map<String,Object> control(@PathVariable String action, Authentication actor) {
        if (!action.equals("pause") && !action.equals("resume")) throw new com.campusflow.common.BusinessException(400,"INVALID_ACTION","仅支持 pause 或 resume");
        simulation.control(action.equals("resume"),actor.getName());
        return overview();
    }
}
