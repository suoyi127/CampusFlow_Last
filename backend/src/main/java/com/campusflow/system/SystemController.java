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
    private final ConfigService configs;
    private final Instant startedAt;
    public SystemController(AccountService accounts, SpaceService spaces, SimulationService simulation, Clock clock,ConfigService configs) {
        this.accounts = accounts; this.spaces = spaces; this.simulation = simulation; this.startedAt = clock.instant();this.configs=configs;
    }
    @GetMapping("/overview") Map<String,Object> overview() {
        var config=configs.current();var result=new java.util.HashMap<String,Object>();
        result.put("version","0.1.0");result.put("startedAt",startedAt);result.put("accountCount",accounts.count());result.put("spaceCount",spaces.all().size());
        result.put("simulationRunId",simulation.runId());result.put("running",simulation.running());result.put("pollSeconds",5);
        result.put("simulationSeconds",config.simulationSeconds());result.put("validitySeconds",config.validitySeconds());result.put("noiseWindowSeconds",config.noiseWindowSeconds());
        return result;
    }
    @PostMapping("/simulation/{action}") Map<String,Object> control(@PathVariable String action, Authentication actor) {
        if (!action.equals("pause") && !action.equals("resume")) throw new com.campusflow.common.BusinessException(400,"INVALID_ACTION","仅支持 pause 或 resume");
        simulation.control(action.equals("resume"),actor.getName());
        return overview();
    }
}
