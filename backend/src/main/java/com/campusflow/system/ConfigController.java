package com.campusflow.system;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/system/config")
public class ConfigController {
    private final ConfigService service;
    public ConfigController(ConfigService service) {this.service=service;}
    @GetMapping RuntimeConfig get() {return service.current();}
    @PutMapping RuntimeConfig update(@Valid @RequestBody ConfigInput input,Authentication actor) {return service.update(input,actor.getName());}
}
