package com.campusflow.hardware;

import com.campusflow.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Component
@Order(50)
public class HardwareBindings implements ApplicationRunner {
 private final JdbcTemplate jdbc;
 private final String configured;
 private final Clock clock;
 public HardwareBindings(JdbcTemplate jdbc,@Value("${campusflow.checkin.device-bindings:}") String configured,Clock clock){this.jdbc=jdbc;this.configured=configured;this.clock=clock;}
 @Override @Transactional public void run(ApplicationArguments args){
  // 旧配置只首次导入，不覆盖管理员后来维护的绑定和启停状态。
  var parsed=new LinkedHashMap<String,Long>();
  for(String entry:configured.split(",")){
   if(entry.isBlank()) continue;
   var pair=entry.trim().split("=",-1);
   if(pair.length!=2 || !pair[0].matches("[A-Za-z0-9_-]{1,64}") || !pair[1].matches("[1-9][0-9]*")) throw new IllegalArgumentException("设备绑定格式应为 AREA_A_001=1,AREA_B_001=2");
   long space=Long.parseLong(pair[1]);
   if(parsed.containsKey(pair[0]) || parsed.containsValue(space)) throw new IllegalArgumentException("设备和空间绑定不能重复");
   parsed.put(pair[0],space);
  }
  for(var entry:parsed.entrySet()){
   if(jdbc.queryForObject("SELECT COUNT(*) FROM hardware_device WHERE device_id=?",Long.class,entry.getKey())>0) continue;
   if(containsSpace(entry.getValue())) continue;
   var now=LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);
   jdbc.update("INSERT INTO hardware_device(device_id,name,space_id,enabled,created_at,updated_at) VALUES(?,?,?,TRUE,?,?)",entry.getKey(),entry.getKey(),entry.getValue(),now,now);
  }
 }
 public Long space(String device){
  var rows=jdbc.queryForList("SELECT device_id,space_id FROM hardware_device WHERE device_id=?",device);
  // MySQL 默认排序规则可能忽略大小写，物理标识仍须精确匹配固件。
  if(rows.isEmpty() || !device.equals(rows.getFirst().get("device_id")) || rows.getFirst().get("space_id")==null) return null;
  return ((Number)rows.getFirst().get("space_id")).longValue();
 }
 public boolean containsSpace(long space){return jdbc.queryForObject("SELECT COUNT(*) FROM hardware_device WHERE space_id=?",Long.class,space)>0;}
 public String device(long space){return jdbc.queryForObject("SELECT device_id FROM hardware_device WHERE space_id=?",String.class,space);}
 public void requireEnabled(String device,long space){
  var rows=jdbc.queryForList("SELECT space_id,enabled FROM hardware_device WHERE device_id=? FOR UPDATE",device);
  if(rows.isEmpty() || rows.getFirst().get("space_id")==null || ((Number)rows.getFirst().get("space_id")).longValue()!=space) throw new BusinessException(409,"DEVICE_BINDING_CHANGED","设备绑定已改变，请重试");
  if(!Boolean.TRUE.equals(rows.getFirst().get("enabled"))) throw new BusinessException(409,"DEVICE_DISABLED","设备已停用");
 }
}
