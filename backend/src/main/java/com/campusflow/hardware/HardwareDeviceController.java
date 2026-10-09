package com.campusflow.hardware;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @Validated @RequestMapping("/api/data/hardware-devices")
public class HardwareDeviceController {
 private final HardwareDeviceService service;
 private final HardwareDiscovery discovery;
 public HardwareDeviceController(HardwareDeviceService service,HardwareDiscovery discovery){this.service=service;this.discovery=discovery;}
 @GetMapping public List<Map<String,Object>> list(){return service.list();}
 @GetMapping("/candidates") public List<Map<String,Object>> candidates(){return discovery.candidates();}
 @PostMapping public List<Map<String,Object>> create(@Valid @RequestBody HardwareDeviceInput input,Authentication actor){return service.save(null,input,actor.getName());}
 @PutMapping("/{id}") public List<Map<String,Object>> update(@PathVariable @Positive long id,@Valid @RequestBody HardwareDeviceInput input,Authentication actor){return service.save(id,input,actor.getName());}
}
