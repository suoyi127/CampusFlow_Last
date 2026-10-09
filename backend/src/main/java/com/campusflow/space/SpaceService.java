package com.campusflow.space;

import com.campusflow.common.BusinessException;
import com.campusflow.system.AuditService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campusflow.simulation.SimulationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SpaceService {
    private final SpaceMapper mapper;
    private final SimulationService simulation;
    private final AuditService audit;
    public SpaceService(SpaceMapper mapper, SimulationService simulation, AuditService audit) {
        this.mapper = mapper; this.simulation = simulation; this.audit = audit;
    }
    public List<StudySpace> all() { return mapper.selectList(new QueryWrapper<StudySpace>().orderByAsc("id")); }
    public StudySpace get(long id) {
        var space = mapper.selectById(id);
        if (space == null) throw new BusinessException(404, "SPACE_NOT_FOUND", "空间不存在");
        return space;
    }
    @Transactional public StudySpace lockForUpdate(long id) {
        var space=mapper.lock(id);
        if (space==null) throw new BusinessException(404,"SPACE_NOT_FOUND","空间不存在");
        return space;
    }
    @Transactional public StudySpace save(Long id, SpaceInput input, String actor) {
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
        if (id != null) {
            // 与模拟共用空间行锁，容量校验和写入期间不会新增签到。
            long people = simulation.currentPeople(id);
            if (people > input.capacity()) throw new BusinessException(409, "CAPACITY_TOO_SMALL", "容量不能低于当前在场人数 " + people);
        }
        space.name = input.name().trim(); space.type = input.type(); space.address = input.address().trim();
        space.latitude = input.latitude(); space.longitude = input.longitude(); space.capacity = input.capacity();
        space.openTime = input.openTime(); space.closeTime = input.closeTime();
        space.openDays = input.openDays().stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(","));
        space.allDay = input.allDay(); space.facilities = input.facilities().stream().distinct().sorted().collect(Collectors.joining(","));
        space.description = input.description(); space.enabled = input.enabled();
        if (id == null) {
            mapper.insert(space);
            simulation.initializeDevice(space);
        } else {
            mapper.updateById(space);
            simulation.endVisitsIfClosed(space);
        }
        audit.record(actor, id == null ? "SPACE_CREATE" : "SPACE_UPDATE", "space:" + space.id, "空间资料更新");
        return space;
    }
}
