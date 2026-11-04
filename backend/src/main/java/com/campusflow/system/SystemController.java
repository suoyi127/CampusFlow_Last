package com.campusflow.system;

import com.campusflow.auth.AccountService;
import com.campusflow.space.SpaceService;
import com.campusflow.status.InspectionService;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final AccountService accounts;
    private final SpaceService spaces;
    private final ConfigService configs;
    private final InspectionService inspection;
    private final Instant startedAt;

    public SystemController(AccountService accounts, SpaceService spaces, Clock clock,
        ConfigService configs, InspectionService inspection) {
        this.accounts = accounts;
        this.spaces = spaces;
        this.configs = configs;
        this.inspection = inspection;
        this.startedAt = clock.instant();
    }

    @GetMapping("/overview") Map<String, Object> overview() {
        var config = configs.current();
        var devices = inspection.devices();
        var result = new LinkedHashMap<String, Object>();
        result.put("version", "0.1.0");
        result.put("startedAt", startedAt);
        result.put("accountCount", accounts.count());
        result.put("spaceCount", spaces.all().size());
        result.put("running", true);
        result.put("pollSeconds", 5);
        result.put("dataSource", "HARDWARE");
        result.put("deviceCount", devices.size());
        result.put("onlineDeviceCount", devices.stream().filter(row -> "ONLINE".equals(row.get("status"))).count());
        result.put("validitySeconds", config.validitySeconds());
        result.put("noiseWindowSeconds", config.noiseWindowSeconds());
        return result;
    }

    @GetMapping("/devices") List<Map<String, Object>> devices() {
        return inspection.devices();
    }
}
