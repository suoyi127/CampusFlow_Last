package com.campusflow.system;

import com.campusflow.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.Map;

@Service
public class ConfigService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AuditService audit;
    private final Clock clock;
    public ConfigService(JdbcTemplate jdbc,ObjectMapper json,AuditService audit,Clock clock) {
        this.jdbc=jdbc;this.json=json;this.audit=audit;this.clock=clock;
    }
    @Transactional(readOnly=true) public RuntimeConfig current() {
        return read(jdbc.queryForMap("SELECT config_value,version FROM system_config WHERE config_key='RUNTIME'"));
    }
    private RuntimeConfig read(Map<String,Object> row) {
        try {
            var values=json.readTree((String)row.get("config_value"));
            return new RuntimeConfig(((Number)row.get("version")).longValue(),values.get("simulationSeconds").asInt(),values.get("validitySeconds").asInt(),values.get("noiseWindowSeconds").asInt(),
                values.get("distanceWeight").asDouble(),values.get("quietWeight").asDouble(),values.get("freeWeight").asDouble(),values.get("facilityWeight").asDouble());
        } catch (Exception error) {throw new IllegalStateException("持久化运行配置无效",error);}
    }
    @Transactional public RuntimeConfig update(ConfigInput input,String actor) {
        var current=read(jdbc.queryForMap("SELECT config_value,version FROM system_config WHERE config_key='RUNTIME' FOR UPDATE"));
        if (input.expectedVersion()!=current.version()) throw new BusinessException(409,"CONFIG_CHANGED","配置已变化，请重新加载后再保存");
        // 周期、时效及窗口共同校验，避免正常采样在下一轮前过期或窗口遗漏有效读数。
        if (input.validitySeconds()<2*input.simulationSeconds() || input.noiseWindowSeconds()<input.validitySeconds())
            throw new BusinessException(400,"INVALID_CONFIG","有效期至少为模拟周期的两倍，噪声窗口不能小于有效期");
        double sum=0;
        for (double value:new double[]{input.distanceWeight(),input.quietWeight(),input.freeWeight(),input.facilityWeight()}) {
            if (!Double.isFinite(value) || value<0 || value>1) throw new BusinessException(400,"INVALID_CONFIG","权重必须为 0 到 1 的有限数值");
            sum+=value;
        }
        if (Math.abs(sum-1)>1e-9) throw new BusinessException(400,"INVALID_CONFIG","四项权重之和必须为 1");
        var next=new RuntimeConfig(current.version()+1,input.simulationSeconds(),input.validitySeconds(),input.noiseWindowSeconds(),
            input.distanceWeight(),input.quietWeight(),input.freeWeight(),input.facilityWeight());
        try {
            jdbc.update("UPDATE system_config SET config_value=?,version=?,updated_at=? WHERE config_key='RUNTIME'",json.writeValueAsString(next),next.version(),LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {throw new IllegalStateException(error);}
        audit.record(actor,"CONFIG_UPDATE","config:RUNTIME","version="+next.version());
        return next;
    }
}
