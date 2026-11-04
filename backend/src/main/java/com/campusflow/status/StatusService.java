package com.campusflow.status;

import com.campusflow.space.StudySpace;
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
    private final Clock clock;
    private final ConfigService configs;
    private final com.campusflow.hardware.HardwareService hardware;
    public StatusService(JdbcTemplate jdbc, Clock clock,ConfigService configs,com.campusflow.hardware.HardwareService hardware) {
        this.jdbc = jdbc; this.clock = clock;this.configs=configs;
        this.hardware=hardware;
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ) public SpaceStatus current(StudySpace space) { return current(space, clock.instant()); }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public SpaceStatus current(StudySpace space, Instant now) {
        if(hardware.bound(space.id)) return hardware.current(space,now,0);
        // 未绑定设备时不推断人数或噪声，也不回退历史模拟表。
        return new SpaceStatus(space.id, 0, null, null, space.capacity, null, null, null,
            null, "MISSING", "MISSING", "OFFLINE", null, null, now,
            configs.current().validitySeconds(), "HARDWARE");
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
