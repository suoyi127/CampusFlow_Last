package com.campusflow.hardware;
import com.campusflow.common.BusinessException;
import com.campusflow.space.SpaceMapper;
import com.campusflow.system.AuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class HardwareDeviceService {
 private final JdbcTemplate jdbc;
 private final SpaceMapper spaces;
 private final AuditService audit;
 private final Clock clock;
 public HardwareDeviceService(JdbcTemplate jdbc,SpaceMapper spaces,AuditService audit,Clock clock){this.jdbc=jdbc;this.spaces=spaces;this.audit=audit;this.clock=clock;}
 public List<Map<String,Object>> list(){
  return jdbc.query("SELECT d.*,s.name AS space_name,s.address AS space_address,(SELECT MAX(r.received_at) FROM hardware_record r WHERE r.device_id=d.device_id) AS last_received_at FROM hardware_device d LEFT JOIN study_space s ON s.id=d.space_id ORDER BY d.id",(rs,i)->{
   var row=new LinkedHashMap<String,Object>();
   row.put("id",rs.getLong("id"));row.put("deviceId",rs.getString("device_id"));row.put("name",rs.getString("name"));
   row.put("spaceId",rs.getObject("space_id"));row.put("spaceName",rs.getString("space_name"));row.put("spaceAddress",rs.getString("space_address"));
   row.put("enabled",rs.getBoolean("enabled"));row.put("version",rs.getLong("version"));
   var last=rs.getObject("last_received_at",LocalDateTime.class);
   row.put("lastReceivedAt",last==null?null:last.toInstant(ZoneOffset.UTC));return row;
  });
 }
 @Transactional public List<Map<String,Object>> save(Long id,HardwareDeviceInput input,String actor){
  // 与模拟、空间维护和接收采用同一顺序锁空间，避免绑定变更与上报交错。
  var all=spaces.lockAll();
  if(input.spaceId()!=null && all.stream().noneMatch(s->Objects.equals(s.id,input.spaceId()))) throw new BusinessException(404,"SPACE_NOT_FOUND","学习空间不存在");
  if(id!=null){
   var rows=jdbc.queryForList("SELECT * FROM hardware_device WHERE id=? FOR UPDATE",id);
   if(rows.isEmpty()) throw new BusinessException(404,"DEVICE_NOT_FOUND","设备不存在");
   var old=rows.getFirst();
   if(input.version()==null || ((Number)old.get("version")).longValue()!=input.version()) throw new BusinessException(409,"VERSION_CONFLICT","设备已被修改，请刷新列表后重新编辑");
   if(!old.get("device_id").equals(input.deviceId())) throw new BusinessException(409,"DEVICE_ID_IMMUTABLE","物理设备ID不能修改，请录入新设备");
   Long oldSpace=old.get("space_id")==null?null:((Number)old.get("space_id")).longValue();
   // 离线消息没有绑定版本，已有数据后换绑会将旧空间事实写入新空间。
   if(!Objects.equals(oldSpace,input.spaceId()) && jdbc.queryForObject("SELECT COUNT(*) FROM hardware_record WHERE device_id=?",Long.class,input.deviceId())>0) throw new BusinessException(409,"DEVICE_HAS_HISTORY","设备已有上传记录，不能换绑或解绑；迁移位置请使用新的设备ID并停用旧设备");
  }
  if(jdbc.queryForObject("SELECT COUNT(*) FROM hardware_device WHERE device_id=? AND (? IS NULL OR id<>?)",Long.class,input.deviceId(),id,id)>0) throw new BusinessException(409,"DEVICE_EXISTS","设备ID已录入");
  // 区域总人数不能把多台计数器相加；一个空间最多绑定一台设备。
  if(input.spaceId()!=null && jdbc.queryForObject("SELECT COUNT(*) FROM hardware_device WHERE space_id=? AND (? IS NULL OR id<>?)",Long.class,input.spaceId(),id,id)>0) throw new BusinessException(409,"SPACE_ALREADY_BOUND","该空间已绑定其他设备（包括停用设备）");
  var now=LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);
  if(id==null) jdbc.update("INSERT INTO hardware_device(device_id,name,space_id,enabled,created_at,updated_at) VALUES(?,?,?,?,?,?)",input.deviceId(),input.name().trim(),input.spaceId(),input.enabled(),now,now);
  else jdbc.update("UPDATE hardware_device SET name=?,space_id=?,enabled=?,version=version+1,updated_at=? WHERE id=?",input.name().trim(),input.spaceId(),input.enabled(),now,id);
  audit.record(actor,id==null?"HARDWARE_DEVICE_CREATE":"HARDWARE_DEVICE_UPDATE",input.deviceId(),"维护设备及空间绑定");
  return list();
 }
}
