package com.campusflow.simulation;

import com.campusflow.common.BusinessException;
import com.campusflow.space.*;
import com.campusflow.system.AuditService;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Statement;
import java.time.*;
import java.util.*;

@Service
@Order(100)
public class SimulationService implements ApplicationRunner {
    public enum Scenario { NORMAL, PEAK, NOISE_EVENT, DEVICE_OFFLINE }
    public record Info(long simulationRunId, boolean running, Scenario scenario, long seed, Long targetSpaceId, Instant eventEndsAt) {}
    private final SpaceMapper spaces;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AuditService audit;
    private final TransactionTemplate transaction;
    private final com.campusflow.hardware.HardwareService hardware;
    private volatile long runId;
    private boolean running = true;
    private Scenario scenario = Scenario.NORMAL;
    private long seed = 127, step = 0, person = 0;
    private Long targetSpaceId;
    private Instant eventEndsAt;
    public SimulationService(SpaceMapper spaces, JdbcTemplate jdbc, Clock clock, AuditService audit, PlatformTransactionManager manager,com.campusflow.hardware.HardwareService hardware) {
        this.spaces = spaces; this.jdbc = jdbc; this.clock = clock; this.audit = audit;
        transaction = new TransactionTemplate(manager);
        this.hardware=hardware;
    }
    @Override public void run(ApplicationArguments args) { mutate(this::startRun); }
    // 在查询事务自己的数据库快照中选轮次，避免读到重置尚未提交的内存编号。
    public long runId() { return Objects.requireNonNull(jdbc.queryForObject("SELECT MAX(id) FROM simulation_run", Long.class)); }
    public synchronized boolean running() { return running; }
    public synchronized Info info() { return new Info(runId,running,scenario,seed,targetSpaceId,eventEndsAt); }
    // 互斥区覆盖事务提交；资料维护只用空间行锁，不反向获取此监视器。
    private synchronized void mutate(Runnable work) {
        var previous = info(); long previousStep = step, previousPerson = person;
        try { transaction.executeWithoutResult(status -> work.run()); }
        catch (RuntimeException failure) {
            runId=previous.simulationRunId(); running=previous.running(); scenario=previous.scenario(); seed=previous.seed();
            targetSpaceId=previous.targetSpaceId(); eventEndsAt=previous.eventEndsAt(); step=previousStep; person=previousPerson;
            throw failure;
        }
    }
    private LocalDateTime time() { return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC); }
    private void startRun() {
        var all=spaces.lockAll(); var now=time();
        jdbc.update("UPDATE sim_visit SET checked_out_at=? WHERE checked_out_at IS NULL",now);
        var key=new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement=connection.prepareStatement("INSERT INTO simulation_run(started_at,scenario,seed) VALUES(?,'NORMAL',127)",Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1,now); return statement;
        },key);
        runId=Objects.requireNonNull(key.getKey()).longValue(); scenario=Scenario.NORMAL; seed=127; step=0; person=0; targetSpaceId=null; eventEndsAt=null;
        jdbc.update("UPDATE noise_device SET status='ONLINE'");
        var random=new Random(seed);
        for(var space:all) {
            if(!space.enabled || hardware.bound(space.id)) continue;
            boolean open=OpeningHours.covers(space,clock.instant(),0);
            int initial=open ? (int)Math.floor(space.capacity*(0.2+random.nextDouble()*0.3)) : 0;
            for(int i=0;i<initial;i++) checkIn(space,now);
            snapshot(space,initial,now); if(open) sample(space,now,random);
        }
    }
    public long currentPeople(long spaceId) {
        if(hardware.bound(spaceId)) return hardware.currentPeople(spaceId);
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM sim_visit WHERE space_id=? AND checked_out_at IS NULL",Long.class,spaceId));
    }
    @Transactional public void initializeDevice(StudySpace space) {
        jdbc.update("INSERT INTO noise_device(space_id,device_code,status,base_db,fluctuation_db) VALUES(?,?,'ONLINE',45,5)",space.id,"SIM-NOISE-"+space.id);
    }
    @Transactional public void endVisitsIfClosed(StudySpace space) {
        // 暂停不阻止营业结束或停用清退；调用方持有空间锁，与重置协调。
        if(space.enabled && OpeningHours.covers(space,clock.instant(),0)) return;
        var now=time();
        int ended=jdbc.update("UPDATE sim_visit SET checked_out_at=? WHERE run_id=? AND space_id=? AND checked_out_at IS NULL",now,runId,space.id);
        if(ended>0) snapshot(space,0,now);
    }
    public void tick() { mutate(() -> {
        if(runId==0) return;
        var all=spaces.lockAll(); var now=time();
        for(var space:all) endVisitsIfClosed(space);
        if(!running) return;
        var random=new Random(seed+step++);
        for(var space:all) {
            if(!space.enabled || hardware.bound(space.id) || !OpeningHours.covers(space,clock.instant(),0)) continue;
            var active=jdbc.queryForList("SELECT id FROM sim_visit WHERE run_id=? AND space_id=? AND checked_out_at IS NULL ORDER BY id",Long.class,runId,space.id);
            int people=active.size();
            int delta=scenario==Scenario.PEAK ? Math.max(1,space.capacity/10)+random.nextInt(3) : random.nextInt(7)-3;
            if(delta>0) {
                int arriving=Math.min(delta,space.capacity-people);
                for(int i=0;i<arriving;i++) checkIn(space,now); people+=arriving;
            } else {
                int leaving=Math.min(-delta,people);
                for(int i=0;i<leaving;i++) jdbc.update("UPDATE sim_visit SET checked_out_at=? WHERE id=?",now,active.get(i)); people-=leaving;
            }
            snapshot(space,people,now); sample(space,now,random);
        }
    }); }
    private void checkIn(StudySpace space,LocalDateTime now) {
        // 轮次与递增序号唯一标识虚拟人员，不关联真实账号。
        jdbc.update("INSERT INTO sim_visit(run_id,virtual_person_id,space_id,checked_in_at) VALUES(?,?,?,?)",runId,"virtual-"+runId+"-"+ ++person,space.id,now);
    }
    private void snapshot(StudySpace space,int people,LocalDateTime now) {
        jdbc.update("INSERT INTO space_snapshot(run_id,space_id,current_people,sampled_at,valid) VALUES(?,?,?,?,TRUE)",runId,space.id,people,now);
    }
    private void sample(StudySpace space,LocalDateTime now,Random random) {
        var devices=jdbc.queryForList("SELECT id,base_db,fluctuation_db FROM noise_device WHERE space_id=? AND status='ONLINE'",space.id);
        if(devices.isEmpty()) return;
        var device=devices.getFirst(); double base=((Number)device.get("base_db")).doubleValue();
        if(scenario==Scenario.NOISE_EVENT && Objects.equals(targetSpaceId,space.id) && clock.instant().isBefore(eventEndsAt)) base+=30;
        double fluctuation=((Number)device.get("fluctuation_db")).doubleValue();
        double noise=Math.round(Math.max(30,Math.min(90,base+(random.nextDouble()*2-1)*fluctuation))*10)/10.0;
        jdbc.update("INSERT INTO noise_sample(run_id,device_id,space_id,noise_db,sampled_at,valid) VALUES(?,?,?,?,?,TRUE)",runId,device.get("id"),space.id,noise,now);
    }
    public void control(boolean enabled,String actor) { mutate(() -> {
        audit.record(actor,enabled ? "SIMULATION_RESUME" : "SIMULATION_PAUSE","simulation:"+runId,"控制模拟运行"); running=enabled;
    }); }
    public void reset(String actor) { mutate(() -> {
        spaces.lockAll(); long previousRun=runId;
        jdbc.update("DELETE FROM noise_sample WHERE run_id=?",runId);
        jdbc.update("DELETE FROM space_snapshot WHERE run_id=?",runId);
        jdbc.update("DELETE FROM sim_visit WHERE run_id=?",runId);
        startRun();
        audit.record(actor,"SIMULATION_RESET","simulation:"+runId,"清除运行 "+previousRun+" 的到访、快照和读数；恢复 NORMAL/127；保留账号、空间、评价、配置、日志和运行历史；保持运行开关");
    }); }
    public void changeScenario(Scenario selected,Long spaceId,long selectedSeed,int durationSeconds,String actor) { mutate(() -> {
        spaces.lockAll();
        if(selected==Scenario.NOISE_EVENT || selected==Scenario.DEVICE_OFFLINE) {
            if(spaceId==null) throw new BusinessException(400,"SPACE_REQUIRED","此场景必须指定目标空间");
            var space=spaces.selectById(spaceId);
            if(space==null) throw new BusinessException(404,"SPACE_NOT_FOUND","空间不存在");
            if(!space.enabled) throw new BusinessException(409,"SPACE_DISABLED","目标空间已停用");
        }
        scenario=selected; seed=selectedSeed; step=0;
        targetSpaceId=selected==Scenario.NOISE_EVENT || selected==Scenario.DEVICE_OFFLINE ? spaceId : null;
        eventEndsAt=selected==Scenario.NOISE_EVENT ? clock.instant().plusSeconds(durationSeconds) : null;
        jdbc.update("UPDATE noise_device SET status='ONLINE'");
        if(selected==Scenario.DEVICE_OFFLINE) jdbc.update("UPDATE noise_device SET status='OFFLINE' WHERE space_id=?",spaceId);
        jdbc.update("UPDATE simulation_run SET scenario=?,seed=? WHERE id=?",selected.name(),selectedSeed,runId);
        audit.record(actor,"SIMULATION_SCENARIO","simulation:"+runId,selected+"; seed="+selectedSeed+"; target="+targetSpaceId+"; durationSeconds="+durationSeconds);
    }); }
    public void device(long spaceId,boolean online,String actor) { mutate(() -> {
        if(spaces.lock(spaceId)==null) throw new BusinessException(404,"SPACE_NOT_FOUND","空间不存在");
        jdbc.update("UPDATE noise_device SET status=? WHERE space_id=?",online ? "ONLINE" : "OFFLINE",spaceId);
        audit.record(actor,"DEVICE_STATUS","space:"+spaceId,online ? "设备恢复在线" : "设备设为离线");
    }); }
}
