package com.campusflow.status;

import com.campusflow.common.BusinessException;
import com.campusflow.space.SpaceMapper;
import com.campusflow.system.AuditService;
import com.campusflow.system.ConfigService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service
public class InspectionService {
    public enum Kind { PEOPLE, NOISE, VISIT }
    private final JdbcTemplate jdbc;
    private final SpaceMapper spaces;
    private final AuditService audit;
    private final Clock clock;
    private final ConfigService configs;

    public InspectionService(JdbcTemplate jdbc, SpaceMapper spaces, AuditService audit,
        Clock clock, ConfigService configs) {
        this.jdbc = jdbc;
        this.spaces = spaces;
        this.audit = audit;
        this.clock = clock;
        this.configs = configs;
    }

    private String restriction(Kind kind) {
        return switch (kind) {
            case PEOPLE -> "1=1";
            case NOISE -> "kind='TELEMETRY'";
            case VISIT -> "kind='EVENT'";
        };
    }

    private String validityColumn(Kind kind) {
        return kind == Kind.NOISE ? "noise_valid" : "people_valid";
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> records(Kind kind, Long spaceId, Boolean valid, int page, int pageSize) {
        if (kind == Kind.VISIT && valid != null)
            throw new BusinessException(400, "INVALID_FILTER", "设备事件不使用有效标记筛选");
        String where = " WHERE " + restriction(kind);
        var params = new ArrayList<Object>();
        if (spaceId != null) {
            where += " AND space_id=?";
            params.add(spaceId);
        }
        if (valid != null) {
            where += " AND " + validityColumn(kind) + "=?";
            params.add(valid);
        }
        long total = Objects.requireNonNull(jdbc.queryForObject(
            "SELECT COUNT(*) FROM hardware_record" + where, Long.class, params.toArray()));
        params.add(pageSize);
        params.add((long) (page - 1) * pageSize);
        var rows = jdbc.query("SELECT * FROM hardware_record" + where + " ORDER BY id DESC LIMIT ? OFFSET ?", (rs, index) -> {
            var row = new LinkedHashMap<String, Object>();
            row.put("id", rs.getLong("id"));
            row.put("spaceId", rs.getLong("space_id"));
            row.put("deviceId", rs.getString("device_id"));
            row.put("sampledAt", rs.getObject("sampled_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
            row.put("receivedAt", rs.getObject("received_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
            if (kind == Kind.VISIT) {
                row.put("eventId", rs.getString("event_id"));
                row.put("eventType", rs.getString("event_type"));
                row.put("headcount", rs.getLong("headcount"));
            } else {
                row.put("valid", rs.getBoolean(validityColumn(kind)));
                row.put("invalidReason", rs.getString(kind == Kind.NOISE ? "noise_invalid_reason" : "people_invalid_reason"));
                if (kind == Kind.PEOPLE) row.put("currentPeople", rs.getLong("headcount"));
                else {
                    row.put("noiseDb", rs.getObject("decibel"));
                    row.put("soundValid", rs.getObject("sound_valid"));
                }
            }
            return row;
        }, params.toArray());
        return Map.of("items", rows, "total", total, "page", page, "pageSize", pageSize,
            "calculatedAt", clock.instant(), "warnings", List.of());
    }

    public List<Map<String, Object>> devices() {
        var now = clock.instant();
        int validity = configs.current().validitySeconds();
        return jdbc.query("SELECT d.*,s.name AS space_name,(SELECT MAX(r.received_at) FROM hardware_record r WHERE r.device_id=d.device_id) AS last_received_at FROM hardware_device d LEFT JOIN study_space s ON s.id=d.space_id ORDER BY d.id", (rs, index) -> {
            var row = new LinkedHashMap<String, Object>();
            var received = rs.getObject("last_received_at", LocalDateTime.class);
            var last = received == null ? null : received.toInstant(ZoneOffset.UTC);
            row.put("id", rs.getLong("id"));
            row.put("spaceId", rs.getObject("space_id"));
            row.put("spaceName", rs.getString("space_name"));
            row.put("deviceId", rs.getString("device_id"));
            row.put("deviceCode", rs.getString("device_id"));
            row.put("name", rs.getString("name"));
            row.put("enabled", rs.getBoolean("enabled"));
            row.put("status", rs.getBoolean("enabled") && "VALID".equals(StatusService.state(last, now, validity)) ? "ONLINE" : "OFFLINE");
            row.put("lastReceivedAt", last);
            return row;
        });
    }

    @Transactional public void validity(Kind kind, long id, boolean valid, String reason, String actor) {
        if (kind == Kind.VISIT)
            throw new BusinessException(400, "INVALID_KIND", "设备事件为只读事实，请检查对应人数记录");
        if (reason == null || reason.isBlank() || reason.trim().length() > 500)
            throw new BusinessException(400, "REASON_REQUIRED", "标记和恢复均须填写1至500字原因");
        spaces.lockAll();
        var rows = jdbc.queryForList("SELECT id FROM hardware_record WHERE id=? AND " + restriction(kind) + " FOR UPDATE", id);
        if (rows.isEmpty()) throw new BusinessException(404, "RECORD_NOT_FOUND", "硬件记录不存在");
        String column = validityColumn(kind);
        String reasonColumn = kind == Kind.NOISE ? "noise_invalid_reason" : "people_invalid_reason";
        jdbc.update("UPDATE hardware_record SET " + column + "=?," + reasonColumn + "=? WHERE id=?", valid, valid ? null : reason.trim(), id);
        audit.record(actor, valid ? "RECORD_RESTORE" : "RECORD_INVALIDATE", kind + ":" + id, reason.trim());
    }
}
