package com.campusflow.hardware;

import com.campusflow.common.BusinessException;
import com.campusflow.space.*;
import com.campusflow.status.*;
import com.campusflow.system.ConfigService;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import static com.campusflow.hardware.HardwarePayload.*;

@Service
public class HardwareService {
    private final JdbcTemplate jdbc;
    private final HardwareBindings bindings;
    private final SpaceMapper spaces;
    private final Clock clock;
    private final Validator validator;
    private final ConfigService configs;
    private final HardwareDiscovery discovery;
    public HardwareService(JdbcTemplate jdbc, HardwareBindings bindings, SpaceMapper spaces, Clock clock, Validator validator, ConfigService configs,HardwareDiscovery discovery) {
        this.jdbc=jdbc; this.bindings=bindings; this.spaces=spaces; this.clock=clock; this.validator=validator; this.configs=configs;
        this.discovery=discovery;
    }
    private void validate(Object input, long timestamp) {
        if (!validator.validate(input).isEmpty()) throw new BusinessException(400,"INVALID_INPUT","设备数据格式不正确");
        // 允许离线历史补传，但不接收明显未来时间，以免长期占据最新记录位置。
        if (timestamp > clock.instant().getEpochSecond()+60) throw new BusinessException(400,"FUTURE_TIMESTAMP","设备时间超过服务器时间60秒");
    }
    private long lock(String device) {
        Long id=bindings.space(device);
        if(id==null) {
            // 只有通过完整载荷校验（MQTT 还须主题一致）的设备才进入发现列表。
            discovery.observe(device);
            throw new BusinessException(404,"DEVICE_NOT_BOUND","已发现设备，请在设备管理中选择并绑定学习空间");
        }
        var space=spaces.lock(id);
        if(space==null) throw new BusinessException(404,"SPACE_NOT_FOUND","绑定的空间不存在");
        bindings.requireEnabled(device,id);
        // 锁与空间维护共用；接收历史事实不受营业时间或当前启停状态影响。
        return id;
    }
    @Transactional public Receipt event(Event input) {
        if (!validator.validate(input).isEmpty()) throw new BusinessException(400,"INVALID_INPUT","签到签退数据格式不正确");
        validate(input,input.timestamp());
        var sequence=new BigDecimal(input.eventId());
        if(sequence.compareTo(new BigDecimal("18446744073709551615"))>0)
            throw new BusinessException(400,"INVALID_EVENT_ID","事件编号超过uint64范围");
        long space=lock(input.deviceId());
        var previous=jdbc.queryForList("SELECT * FROM hardware_record WHERE device_id=? AND event_id=?",input.deviceId(),input.eventId());
        if(!previous.isEmpty()) {
            var row=previous.getFirst();
            if(((Number)row.get("space_id")).longValue()!=space || !input.eventType().name().equals(row.get("event_type"))
                || ((Number)row.get("headcount")).longValue()!=input.headcount()
                || !time(row.get("sampled_at")).equals(Instant.ofEpochSecond(input.timestamp())))
                throw new BusinessException(409,"EVENT_CONFLICT","同一设备事件编号已对应其他内容");
            return new Receipt(((Number)row.get("id")).longValue(),space,input.deviceId(),true);
        }
        // 使用设备上报的绝对人数，不再加减一次；空人数签退和离线缺段因此不会导致负数。
        return insert(space,input.deviceId(),Kind.EVENT,input.eventId(),sequence,input.eventType().name(),input.timestamp(),input.headcount(),null,null);
    }
    @Transactional public Receipt telemetry(Telemetry input) {
        if (!validator.validate(input).isEmpty()) throw new BusinessException(400,"INVALID_INPUT","环境数据格式不正确");
        validate(input,input.timestamp());
        if(Boolean.TRUE.equals(input.soundValid()) != (input.decibel()!=null)
            || (input.decibel()!=null && !Double.isFinite(input.decibel())))
            throw new BusinessException(400,"INVALID_SOUND","声音有效时必须有分贝，无效时分贝必须为null");
        long space=lock(input.deviceId());
        return insert(space,input.deviceId(),Kind.TELEMETRY,null,null,null,input.timestamp(),input.headcount(),input.decibel(),input.soundValid());
    }
    private Receipt insert(long space,String device,Kind kind,String eventId,BigDecimal sequence,String eventType,long timestamp,long count,Double db,Boolean valid) {
        var key=new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement=connection.prepareStatement("INSERT INTO hardware_record(device_id,space_id,kind,event_id,event_sequence,event_type,headcount,decibel,sound_valid,sampled_at,received_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
            Object[] values={device,space,kind.name(),eventId,sequence,eventType,count,db,valid,LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp),ZoneOffset.UTC),LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC)};
            for(int i=0;i<values.length;i++) statement.setObject(i+1,values[i]);
            return statement;
        },key);
        return new Receipt(Objects.requireNonNull(key.getKey()).longValue(),space,device,false);
    }
    @Transactional public void reject(String topic,String payload,String code) {
        jdbc.update("INSERT INTO hardware_rejection(topic,payload,error_code,received_at) VALUES(?,?,?,?)",topic.substring(0,Math.min(topic.length(),200)),payload.substring(0,Math.min(payload.length(),8192)),code,LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));
    }
    private static Instant time(Object value) {
        return (value instanceof LocalDateTime local ? local : ((Timestamp)value).toLocalDateTime()).toInstant(ZoneOffset.UTC);
    }
    public boolean bound(long space) { return bindings.containsSpace(space); }
    public long currentPeople(long space) {
        var counts=jdbc.queryForList("SELECT headcount FROM hardware_record WHERE space_id=? AND device_id=? ORDER BY sampled_at DESC,CASE WHEN kind='EVENT' THEN 1 ELSE 0 END DESC,event_sequence DESC,id DESC LIMIT 1",Long.class,space,bindings.device(space));
        return counts.isEmpty()?0:counts.getFirst();
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public SpaceStatus current(StudySpace space,Instant now,long run) {
        var config=configs.current(); String device=bindings.device(space.id);
        // 同秒内的多个按键用数值序号排序；事件优先于同秒遥测，历史重投不改变排序。
        var peopleRows=jdbc.queryForList("SELECT * FROM hardware_record WHERE space_id=? AND device_id=? ORDER BY sampled_at DESC,CASE WHEN kind='EVENT' THEN 1 ELSE 0 END DESC,event_sequence DESC,id DESC LIMIT 1",space.id,device);
        var noiseRows=jdbc.queryForList("SELECT * FROM hardware_record WHERE space_id=? AND device_id=? AND kind='TELEMETRY' ORDER BY sampled_at DESC,id DESC LIMIT 1",space.id,device);
        var lastReceipt=jdbc.queryForObject("SELECT MAX(received_at) FROM hardware_record WHERE space_id=? AND device_id=?",LocalDateTime.class,space.id,device);
        String online=lastReceipt!=null && "VALID".equals(StatusService.state(lastReceipt.toInstant(ZoneOffset.UTC),now,config.validitySeconds())) ? "ONLINE" : "OFFLINE";
        Instant peopleAt=peopleRows.isEmpty()?null:time(peopleRows.getFirst().get("sampled_at"));
        Instant noiseAt=noiseRows.isEmpty()?null:time(noiseRows.getFirst().get("sampled_at"));
        String peopleState=StatusService.state(peopleAt,now,config.validitySeconds());
        String noiseState=StatusService.state(noiseAt,now,config.validitySeconds());
        long count=peopleRows.isEmpty()?0:((Number)peopleRows.getFirst().get("headcount")).longValue();
        if(count>space.capacity) peopleState="INVALID";
        if(!noiseRows.isEmpty() && !Boolean.TRUE.equals(noiseRows.getFirst().get("sound_valid"))) noiseState="INVALID";
        if("OFFLINE".equals(online)) noiseState="OFFLINE";
        Integer people="VALID".equals(peopleState)?(int)count:null;
        Double db="VALID".equals(noiseState)?((Number)noiseRows.getFirst().get("decibel")).doubleValue():null;
        Double typical=null;
        if(db!=null) {
            var window=jdbc.queryForList("SELECT decibel FROM hardware_record WHERE space_id=? AND device_id=? AND kind='TELEMETRY' AND sound_valid=TRUE AND sampled_at>=? AND sampled_at<=? ORDER BY decibel",Double.class,space.id,device,LocalDateTime.ofInstant(now.minusSeconds(config.noiseWindowSeconds()),ZoneOffset.UTC),LocalDateTime.ofInstant(now,ZoneOffset.UTC));
            if(!window.isEmpty()) typical=StatusService.median(window);
        }
        return new SpaceStatus(space.id,run,peopleRows.isEmpty()?null:((Number)peopleRows.getFirst().get("id")).longValue(),people,space.capacity,people==null?null:(double)people/space.capacity,db,typical,typical==null?null:StatusService.quietLevel(typical),peopleState,noiseState,online,peopleAt,noiseAt,now,config.validitySeconds(),"HARDWARE");
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> records(Long space,String device,Kind kind,int page,int size) {
        String where=" WHERE 1=1"; var params=new ArrayList<Object>();
        if(space!=null) { where+=" AND space_id=?";params.add(space); }
        if(device!=null) { where+=" AND device_id=?";params.add(device); }
        if(kind!=null) { where+=" AND kind=?";params.add(kind.name()); }
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM hardware_record"+where,Long.class,params.toArray()));
        params.add(size);params.add((long)(page-1)*size);
        var items=jdbc.query("SELECT * FROM hardware_record"+where+" ORDER BY id DESC LIMIT ? OFFSET ?",(rs,index)-> {
            var row=new LinkedHashMap<String,Object>();
            row.put("id",rs.getLong("id"));row.put("deviceId",rs.getString("device_id"));row.put("spaceId",rs.getLong("space_id"));
            row.put("kind",rs.getString("kind"));row.put("eventId",rs.getString("event_id"));row.put("eventType",rs.getString("event_type"));
            row.put("headcount",rs.getLong("headcount"));row.put("decibel",rs.getObject("decibel"));row.put("soundValid",rs.getObject("sound_valid"));
            row.put("sampledAt",rs.getObject("sampled_at",LocalDateTime.class).toInstant(ZoneOffset.UTC));row.put("receivedAt",rs.getObject("received_at",LocalDateTime.class).toInstant(ZoneOffset.UTC));return row;
        },params.toArray());
        return Map.of("items",items,"total",total,"page",page,"pageSize",size,"calculatedAt",clock.instant(),"warnings",List.of());
    }
}
