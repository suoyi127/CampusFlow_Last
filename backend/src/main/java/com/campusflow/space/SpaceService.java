package com.campusflow.space;

import com.campusflow.common.BusinessException;
import com.campusflow.system.AuditService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campusflow.hardware.HardwareService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SpaceService {
    private final SpaceMapper mapper;
    private final HardwareService hardware;
    private final AuditService audit;
    public SpaceService(SpaceMapper mapper, HardwareService hardware, AuditService audit) {
        this.mapper = mapper; this.hardware = hardware; this.audit = audit;
    }
    public List<StudySpace> all() { return mapper.selectList(new QueryWrapper<StudySpace>().eq("data_origin", "REAL").orderByAsc("id")); }
    public StudySpace get(long id) {
        var space = mapper.selectById(id);
        if (space == null || !"REAL".equals(space.dataOrigin)) throw new BusinessException(404, "SPACE_NOT_FOUND", "空间不存在");
        return space;
    }
    @Transactional public StudySpace lockForUpdate(long id) {
        var space=mapper.lock(id);
        if (space==null) throw new BusinessException(404,"SPACE_NOT_FOUND","空间不存在");
        return space;
    }
    @Transactional public StudySpace save(Long id, SpaceInput input, String actor) {
        if (input.coordinateSystem()!=null && !"GCJ02".equals(input.coordinateSystem()))
            throw new BusinessException(400,"INVALID_COORDINATES","仅支持高德 GCJ02 坐标");
        if (!Set.of("LIBRARY","CLASSROOM","DISCUSSION","OUTDOOR","STUDY_ROOM","CAFE").contains(input.type()))
            throw new BusinessException(400, "INVALID_TYPE", "空间类型无效");
        if (!Set.of("AC","SEAT","POWER","WIFI").containsAll(input.facilities()))
            throw new BusinessException(400, "INVALID_FACILITY", "设施类型无效");
        if (!Double.isFinite(input.latitude()) || !Double.isFinite(input.longitude()))
            throw new BusinessException(400, "INVALID_COORDINATES", "坐标必须为有限数值");
        if (!input.allDay() && input.openTime().compareTo(input.closeTime()) >= 0)
            throw new BusinessException(400, "INVALID_HOURS", "关闭时间必须晚于开放时间，不支持跨午夜时段");
        var space = id == null ? new StudySpace() : mapper.lock(id);
        if (space == null) throw new BusinessException(404, "SPACE_NOT_FOUND", "空间不存在");
        // 旧客户端省略字段不能冒充管理员对未知历史坐标的显式确认。
        if (id != null && !"GCJ02".equals(space.coordinateSystem) && input.coordinateSystem()==null)
            throw new BusinessException(400,"COORDINATES_UNCONFIRMED","请确认空间位置并明确使用 GCJ02 坐标");
        if (id != null) {
            // 与硬件接收共用空间行锁，容量校验和写入期间不会新增签到。
            if (hardware.bound(id) && input.capacity() < space.capacity) {
                var reading = hardware.capacityPeople(space);
                if (!"VALID".equals(reading.state()))
                    throw new BusinessException(409, "PEOPLE_UNCONFIRMED", "硬件人数未知、过期或无效，请纠正读数后再降低容量");
                if (reading.count() > input.capacity())
                    throw new BusinessException(409, "CAPACITY_TOO_SMALL", "容量不能低于当前在场人数 " + reading.count());
            }
        }
        space.dataOrigin = "REAL";
        space.name = input.name().trim(); space.type = input.type(); space.address = input.address().trim();
        space.latitude = input.latitude(); space.longitude = input.longitude(); space.capacity = input.capacity();
        space.coordinateSystem = "GCJ02";
        space.openTime = input.openTime(); space.closeTime = input.closeTime();
        space.openDays = input.openDays().stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(","));
        space.allDay = input.allDay(); space.facilities = input.facilities().stream().distinct().sorted().collect(Collectors.joining(","));
        space.description = input.description(); space.enabled = input.enabled();
        if (id == null) {
            mapper.insert(space);
        } else {
            mapper.updateById(space);
        }
        audit.record(actor, id == null ? "SPACE_CREATE" : "SPACE_UPDATE", "space:" + space.id, "空间资料更新");
        return space;
    }
}
