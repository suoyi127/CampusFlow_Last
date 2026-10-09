package com.campusflow.hardware;

import com.campusflow.status.StatusService;
import com.campusflow.system.ConfigService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service
public class HardwareDiscovery {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ConfigService configs;
    public HardwareDiscovery(JdbcTemplate jdbc,Clock clock,ConfigService configs) {
        this.jdbc=jdbc;this.clock=clock;this.configs=configs;
    }
    // 发现记录独立提交：尚未绑定的上传事务会拒绝，仍要保留可供管理员选择的设备。
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void observe(String device) {
        var now=LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);
        jdbc.update("INSERT INTO hardware_discovery(device_id,first_seen,last_seen) VALUES(?,?,?) ON DUPLICATE KEY UPDATE last_seen=VALUES(last_seen)",device,now,now);
    }
    public List<Map<String,Object>> candidates() {
        var now=clock.instant();int validity=configs.current().validitySeconds();
        return jdbc.query("SELECT d.* FROM hardware_discovery d WHERE NOT EXISTS(SELECT 1 FROM hardware_device r WHERE r.device_id=d.device_id) ORDER BY last_seen DESC,device_id",(rs,index)-> {
            var last=rs.getObject("last_seen",LocalDateTime.class).toInstant(ZoneOffset.UTC);
            return Map.<String,Object>of("deviceId",rs.getString("device_id"),"firstSeenAt",rs.getObject("first_seen",LocalDateTime.class).toInstant(ZoneOffset.UTC),"lastSeenAt",last,
                "online","VALID".equals(StatusService.state(last,now,validity)));
        });
    }
}
