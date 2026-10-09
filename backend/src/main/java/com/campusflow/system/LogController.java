package com.campusflow.system;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/system/logs")
public class LogController {
    private final AuditService audit;
    public LogController(AuditService audit) {this.audit=audit;}
    @GetMapping Map<String,Object> list(@Valid @ModelAttribute LogQuery query) {return audit.list(query);}
}
