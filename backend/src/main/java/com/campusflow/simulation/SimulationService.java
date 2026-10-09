package com.campusflow.simulation;

import com.campusflow.space.*;
import com.campusflow.system.AuditService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Statement;
import java.time.*;
import java.util.*;

@Service
@Order(100)
public class SimulationService implements ApplicationRunner {
    private final SpaceMapper spaces;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AuditService audit;
    private final Random random = new Random(127);
    private volatile long runId;
    private volatile boolean running = true;
    public SimulationService(SpaceMapper spaces, JdbcTemplate jdbc, Clock clock, AuditService audit) {
        this.spaces = spaces; this.jdbc = jdbc; this.clock = clock; this.audit = audit;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        var now = time();
        jdbc.update("UPDATE sim_visit SET checked_out_at=? WHERE checked_out_at IS NULL", now);
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO simulation_run(started_at,scenario,seed) VALUES(?,'NORMAL',127)", Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1, now); return statement;
        }, key);
        runId = Objects.requireNonNull(key.getKey()).longValue();
        for (var space : spaces.lockAll()) {
            if (!space.enabled || !OpeningHours.covers(space, clock.instant(), 0)) continue;
            int initial = (int) Math.floor(space.capacity * (0.2 + random.nextDouble() * 0.3));
            for (int i = 0; i < initial; i++) checkIn(space, now);
            snapshot(space, initial, now);
            sample(space, now);
        }
    }
    public long runId() { return runId; }
    public boolean running() { return running; }
    public long currentPeople(long spaceId) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM sim_visit WHERE space_id=? AND checked_out_at IS NULL", Long.class, spaceId));
    }
    @Transactional public void initializeDevice(StudySpace space) {
        jdbc.update("INSERT INTO noise_device(space_id,device_code,status,base_db,fluctuation_db) VALUES(?,?,'ONLINE',45,5)", space.id,"SIM-NOISE-" + space.id);
    }
    @Transactional public void endVisitsIfClosed(StudySpace space) {
        // 资料维护即时清退，不依赖定时任务，暂停模拟也不能阻止空间关闭。
        if (space.enabled && OpeningHours.covers(space,clock.instant(),0)) return;
        var now = time();
        int ended = jdbc.update("UPDATE sim_visit SET checked_out_at=? WHERE run_id=? AND space_id=? AND checked_out_at IS NULL",now,runId,space.id);
        if (ended > 0) snapshot(space,0,now);
    }
    private LocalDateTime time() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    @Transactional public synchronized void tick() {
        if (!running || runId == 0) return;
        var now = time();
        for (var space : spaces.lockAll()) {
            var active = jdbc.queryForList("SELECT id FROM sim_visit WHERE run_id=? AND space_id=? AND checked_out_at IS NULL ORDER BY id", Long.class, runId, space.id);
            int people = active.size();
            if (!space.enabled || !OpeningHours.covers(space, clock.instant(), 0)) {
                if (people > 0) {
                    jdbc.update("UPDATE sim_visit SET checked_out_at=? WHERE run_id=? AND space_id=? AND checked_out_at IS NULL", now, runId, space.id);
                    snapshot(space, 0, now);
                }
                continue;
            }
            // 只签退真实存在的在场到访；签到与容量检查共用空间行锁。
            int delta = random.nextInt(7) - 3;
            if (delta > 0) {
                for (int i = 0; i < Math.min(delta, space.capacity - people); i++) checkIn(space, now);
                people += Math.min(delta, space.capacity - people);
            } else {
                int leaving = Math.min(-delta, people);
                for (int i = 0; i < leaving; i++) jdbc.update("UPDATE sim_visit SET checked_out_at=? WHERE id=?", now, active.get(i));
                people -= leaving;
            }
            snapshot(space, people, now);
            sample(space, now);
        }
    }
    private void checkIn(StudySpace space, LocalDateTime now) {
        jdbc.update("INSERT INTO sim_visit(run_id,virtual_person_id,space_id,checked_in_at) VALUES(?,?,?,?)", runId, "virtual-" + UUID.randomUUID(), space.id, now);
    }
    private void snapshot(StudySpace space, int people, LocalDateTime now) {
        jdbc.update("INSERT INTO space_snapshot(run_id,space_id,current_people,sampled_at,valid) VALUES(?,?,?,?,TRUE)", runId, space.id, people, now);
    }
    private void sample(StudySpace space, LocalDateTime now) {
        var devices = jdbc.queryForList("SELECT id,base_db,fluctuation_db FROM noise_device WHERE space_id=? AND status='ONLINE'", space.id);
        if (devices.isEmpty()) return;
        var device = devices.getFirst();
        double base = ((Number) device.get("base_db")).doubleValue();
        double fluctuation = ((Number) device.get("fluctuation_db")).doubleValue();
        double noise = Math.round(Math.max(30, Math.min(90, base + (random.nextDouble() * 2 - 1) * fluctuation)) * 10) / 10.0;
        jdbc.update("INSERT INTO noise_sample(run_id,device_id,space_id,noise_db,sampled_at,valid) VALUES(?,?,?,?,?,TRUE)", runId, device.get("id"), space.id, noise, now);
    }
    @Transactional public synchronized void control(boolean enabled, String actor) {
        audit.record(actor, enabled ? "SIMULATION_RESUME" : "SIMULATION_PAUSE", "simulation:" + runId, "控制模拟运行");
        running = enabled;
    }
}
