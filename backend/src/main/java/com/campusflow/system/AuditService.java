package com.campusflow.system;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public AuditService(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    public void record(String actor, String action, String target, String reason) {
        jdbc.update("INSERT INTO audit_log(actor,action,target,result,reason,occurred_at) VALUES(?,?,?,'SUCCESS',?,?)",
            actor, action, target, reason, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }
}
