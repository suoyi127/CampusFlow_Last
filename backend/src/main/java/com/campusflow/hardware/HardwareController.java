package com.campusflow.hardware;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import static com.campusflow.hardware.HardwarePayload.*;

@RestController
public class HardwareController {
    private final HardwareService service;
    public HardwareController(HardwareService service) { this.service=service; }
    @PostMapping("/api/hardware/events") public Receipt event(@Valid @RequestBody Event input) { return service.event(input); }
    @PostMapping("/api/hardware/telemetry") public Receipt telemetry(@Valid @RequestBody Telemetry input) { return service.telemetry(input); }
    @GetMapping("/api/data/hardware-records") public Map<String,Object> records(
        @RequestParam(required=false) @Positive Long spaceId,
        @RequestParam(required=false) @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String deviceId,
        @RequestParam(required=false) Kind kind,
        @RequestParam(defaultValue="1") @Min(1) @Max(1000000) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return service.records(spaceId,deviceId,kind,page,pageSize);
    }
}
