package com.campusflow.status;

import com.campusflow.space.StudySpace;
import com.campusflow.simulation.SimulationService;
import com.campusflow.system.ConfigService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;

@Service
public class StatusService {
    private final JdbcTemplate jdbc;
    private final SimulationService simulation;
    private final Clock clock;
    private final ConfigService configs;
    public StatusService(JdbcTemplate jdbc, SimulationService simulation, Clock clock,ConfigService configs) {
        this.jdbc = jdbc; this.simulation = simulation; this.clock = clock;this.configs=configs;
    }
    @Transactional(readOnly=true) public SpaceStatus current(StudySpace space) { return current(space, clock.instant()); }
    public SpaceStatus current(StudySpace space, Instant now) {
        var config=configs.current();
        long run = simulation.runId();
        // 容量降低后，异常回退不能重新显示超过现容量的历史人数。
        var peopleRows = jdbc.queryForList("SELECT * FROM space_snapshot WHERE run_id=? AND space_id=? AND valid=TRUE AND current_people<=? ORDER BY sampled_at DESC,id DESC LIMIT 1", run, space.id, space.capacity);
        var noiseRows = jdbc.queryForList("SELECT * FROM noise_sample WHERE run_id=? AND space_id=? AND valid=TRUE ORDER BY sampled_at DESC,id DESC LIMIT 1", run, space.id);
        String device = jdbc.queryForObject("SELECT status FROM noise_device WHERE space_id=?", String.class, space.id);
        Instant peopleAt = peopleRows.isEmpty() ? null : timestamp(peopleRows.getFirst().get("sampled_at"));
        Instant noiseAt = noiseRows.isEmpty() ? null : timestamp(noiseRows.getFirst().get("sampled_at"));
        String peopleState = state(peopleAt, now,config.validitySeconds());
        String noiseState = "OFFLINE".equals(device) ? "OFFLINE" : state(noiseAt, now,config.validitySeconds());
        if (peopleRows.isEmpty()) {
            var invalid = jdbc.queryForList("SELECT sampled_at FROM space_snapshot WHERE run_id=? AND space_id=? ORDER BY sampled_at DESC,id DESC LIMIT 1",run,space.id);
            if (!invalid.isEmpty()) { peopleState = "INVALID"; peopleAt = timestamp(invalid.getFirst().get("sampled_at")); }
        }
        if (noiseRows.isEmpty()) {
            var invalid = jdbc.queryForList("SELECT sampled_at FROM noise_sample WHERE run_id=? AND space_id=? ORDER BY sampled_at DESC,id DESC LIMIT 1",run,space.id);
            if (!invalid.isEmpty()) {
                noiseAt = timestamp(invalid.getFirst().get("sampled_at"));
                if (!"OFFLINE".equals(device)) noiseState = "INVALID";
            }
        }
        Integer people = "VALID".equals(peopleState) ? ((Number) peopleRows.getFirst().get("current_people")).intValue() : null;
        Double latest = "VALID".equals(noiseState) ? ((Number) noiseRows.getFirst().get("noise_db")).doubleValue() : null;
        Double typical = null;
        if (latest != null) {
            // 最新读数有效才计算窗口，中位数不冒充等效声级，也不取分贝算术平均。
            var window = jdbc.queryForList("SELECT noise_db FROM noise_sample WHERE run_id=? AND space_id=? AND valid=TRUE AND sampled_at>=? AND sampled_at<=? ORDER BY noise_db",
                Double.class, run, space.id, LocalDateTime.ofInstant(now.minusSeconds(config.noiseWindowSeconds()), ZoneOffset.UTC), LocalDateTime.ofInstant(now, ZoneOffset.UTC));
            if (!window.isEmpty()) typical = median(window);
        }
        Long version = peopleRows.isEmpty() ? null : ((Number) peopleRows.getFirst().get("id")).longValue();
        return new SpaceStatus(space.id, run, version, people, space.capacity,
            people == null ? null : Math.min(1.0, (double)people / space.capacity), latest, typical,
            typical == null ? null : quietLevel(typical), peopleState, noiseState, device,
            peopleAt, noiseAt, now, config.validitySeconds(), "SIMULATED");
    }
    static Instant timestamp(Object value) {
        // MySQL 驱动返回 LocalDateTime，H2 返回 Timestamp；两者都按约定的 UTC 解释。
        var local = value instanceof LocalDateTime time ? time : ((Timestamp)value).toLocalDateTime();
        return local.toInstant(ZoneOffset.UTC);
    }
    public static String state(Instant updated, Instant now) {
        return state(updated,now,30);
    }
    public static String state(Instant updated,Instant now,int validitySeconds) {
        if (updated == null) return "MISSING";
        return updated.isAfter(now) || Duration.between(updated, now).compareTo(Duration.ofSeconds(validitySeconds)) > 0 ? "EXPIRED" : "VALID";
    }
    public static double median(List<Double> sorted) {
        int size = sorted.size();
        return size % 2 == 1 ? sorted.get(size / 2) : (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2;
    }
    public static int quietLevel(double db) { return db < 45 ? 5 : db < 55 ? 4 : db < 65 ? 3 : db < 75 ? 2 : 1; }
}
