package com.campusflow.status;

import com.campusflow.common.BusinessException;
import com.campusflow.simulation.SimulationService;
import com.campusflow.space.SpaceMapper;
import com.campusflow.system.AuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.util.*;

@Service
public class InspectionService {
    public enum Kind { PEOPLE, NOISE, VISIT }
    private final JdbcTemplate jdbc;
    private final SimulationService simulation;
    private final SpaceMapper spaces;
    private final AuditService audit;
    private final Clock clock;
    public InspectionService(JdbcTemplate jdbc, SimulationService simulation, SpaceMapper spaces, AuditService audit, Clock clock) {
        this.jdbc = jdbc; this.simulation = simulation; this.spaces = spaces; this.audit = audit; this.clock = clock;
    }
    private String table(Kind kind) {
        return switch (kind) { case PEOPLE -> "space_snapshot"; case NOISE -> "noise_sample"; case VISIT -> "sim_visit"; };
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> records(Kind kind, Long spaceId, Boolean valid, int page, int pageSize) {
        if (kind == Kind.VISIT && valid != null) throw new BusinessException(400,"INVALID_FILTER","到访记录不使用有效标记筛选");
        long run = simulation.runId();
        var params = new ArrayList<Object>(); params.add(run);
        String where = " WHERE run_id=?";
        if (spaceId != null) { where += " AND space_id=?"; params.add(spaceId); }
        if (valid != null) { where += " AND valid=?"; params.add(valid); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table(kind) + where,Long.class,params.toArray());
        var queryParams = new ArrayList<>(params); queryParams.add(pageSize); queryParams.add((long)(page-1)*pageSize);
        var rows = jdbc.query("SELECT * FROM " + table(kind) + where + " ORDER BY id DESC LIMIT ? OFFSET ?", (rs,index) -> {
            var row = new LinkedHashMap<String,Object>();
            row.put("id",rs.getLong("id")); row.put("runId",rs.getLong("run_id")); row.put("spaceId",rs.getLong("space_id"));
            if (kind == Kind.VISIT) {
                row.put("virtualPersonId",rs.getString("virtual_person_id"));
                // DATETIME 存的是 UTC 墙上时间；getTimestamp 会被 MySQL 连接时区转换。
                row.put("checkedInAt",rs.getObject("checked_in_at",java.time.LocalDateTime.class).toInstant(java.time.ZoneOffset.UTC));
                var ended = rs.getObject("checked_out_at",java.time.LocalDateTime.class);
                row.put("checkedOutAt",ended == null ? null : ended.toInstant(java.time.ZoneOffset.UTC));
            } else {
                row.put("sampledAt",rs.getObject("sampled_at",java.time.LocalDateTime.class).toInstant(java.time.ZoneOffset.UTC));
                row.put("valid",rs.getBoolean("valid")); row.put("invalidReason",rs.getString("invalid_reason"));
                if (kind == Kind.PEOPLE) row.put("currentPeople",rs.getInt("current_people"));
                else { row.put("noiseDb",rs.getDouble("noise_db")); row.put("deviceId",rs.getLong("device_id")); }
            }
            return row;
        },queryParams.toArray());
        return Map.of("items",rows,"total",Objects.requireNonNull(total),"page",page,"pageSize",pageSize,
            "calculatedAt",clock.instant(),"simulationRunId",run,"warnings",List.of());
    }
    public List<Map<String,Object>> devices() {
        return jdbc.query("SELECT d.*,s.name,s.enabled FROM noise_device d JOIN study_space s ON s.id=d.space_id ORDER BY d.space_id", (rs,index) ->
            Map.of("id",rs.getLong("id"),"spaceId",rs.getLong("space_id"),"spaceName",rs.getString("name"),
                "enabled",rs.getBoolean("enabled"),"deviceCode",rs.getString("device_code"),"status",rs.getString("status"),
                "baseDb",rs.getDouble("base_db"),"fluctuationDb",rs.getDouble("fluctuation_db")));
    }
    @Transactional public void validity(Kind kind, long id, boolean valid, String reason, String actor) {
        if (kind == Kind.VISIT) throw new BusinessException(400,"INVALID_KIND","到访事件不能标记无效，请检查人数快照");
        if (reason == null || reason.isBlank() || reason.trim().length() > 500)
            throw new BusinessException(400,"REASON_REQUIRED","标记和恢复均须填写1至500字原因");
        // 与重置共用空间锁，先锁后读；禁止旧页面修改已清除或非本轮记录。
        spaces.lockAll();
        var rows = jdbc.queryForList("SELECT run_id FROM " + table(kind) + " WHERE id=? FOR UPDATE",id);
        if (rows.isEmpty()) throw new BusinessException(404,"RECORD_NOT_FOUND","记录不存在，可能已被模拟重置清除");
        if (((Number)rows.getFirst().get("run_id")).longValue() != simulation.runId())
            throw new BusinessException(409,"OLD_SIMULATION_RUN","只能修改当前模拟运行的记录，请刷新页面");
        jdbc.update("UPDATE " + table(kind) + " SET valid=?,invalid_reason=? WHERE id=?",valid,valid ? null : reason.trim(),id);
        audit.record(actor,valid ? "RECORD_RESTORE" : "RECORD_INVALIDATE",kind + ":" + id,reason.trim());
    }
}
