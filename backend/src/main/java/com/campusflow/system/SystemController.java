package com.campusflow.system;

import com.campusflow.auth.AccountService;
import com.campusflow.space.SpaceService;
import com.campusflow.simulation.SimulationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final AccountService accounts;
    private final SpaceService spaces;
    private final SimulationService simulation;
    private final ConfigService configs;
    private final com.campusflow.status.InspectionService inspection;
    private final Instant startedAt;
    public SystemController(AccountService accounts, SpaceService spaces, SimulationService simulation, Clock clock,ConfigService configs,com.campusflow.status.InspectionService inspection) {
        this.accounts = accounts; this.spaces = spaces; this.simulation = simulation; this.startedAt = clock.instant();this.configs=configs;this.inspection=inspection;
    }
    @GetMapping("/overview") Map<String,Object> overview() {
        var config=configs.current();var result=new java.util.HashMap<String,Object>();
        var info=simulation.info();
        result.put("version","0.1.0");result.put("startedAt",startedAt);result.put("accountCount",accounts.count());result.put("spaceCount",spaces.all().size());
        result.put("simulationRunId",info.simulationRunId());result.put("running",info.running());result.put("pollSeconds",5);
        result.put("scenario",info.scenario());result.put("seed",info.seed());result.put("targetSpaceId",info.targetSpaceId());result.put("eventEndsAt",info.eventEndsAt());
        result.put("simulationSeconds",config.simulationSeconds());result.put("validitySeconds",config.validitySeconds());result.put("noiseWindowSeconds",config.noiseWindowSeconds());
        return result;
    }
    @PostMapping("/simulation/{action}") Map<String,Object> control(@PathVariable String action, Authentication actor) {
        switch (action) {
            case "pause", "resume" -> simulation.control(action.equals("resume"),actor.getName());
            case "reset" -> simulation.reset(actor.getName());
            default -> throw new com.campusflow.common.BusinessException(400,"INVALID_ACTION","仅支持 pause、resume 或 reset");
        }
        return overview();
    }
    public record ScenarioInput(@NotNull SimulationService.Scenario scenario,@Positive Long spaceId,
        @Min(0) @Max(2147483647) Long seed,@Min(5) @Max(3600) Integer durationSeconds) {}
    @PutMapping("/simulation/scenario") Map<String,Object> scenario(@Valid @RequestBody ScenarioInput input,Authentication actor) {
        simulation.changeScenario(input.scenario(),input.spaceId(),input.seed()==null ? 127 : input.seed(),input.durationSeconds()==null ? 60 : input.durationSeconds(),actor.getName());
        return overview();
    }
    public record DeviceInput(@NotNull Boolean online) {}
    @GetMapping("/devices") java.util.List<Map<String,Object>> devices() { return inspection.devices(); }
    @PutMapping("/devices/{spaceId}") Map<String,Object> device(@PathVariable long spaceId,@Valid @RequestBody DeviceInput input,Authentication actor) {
        simulation.device(spaceId,input.online(),actor.getName()); return overview();
    }
}
