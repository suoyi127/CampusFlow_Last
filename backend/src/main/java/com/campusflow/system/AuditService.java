package com.campusflow.system;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import com.campusflow.common.BusinessException;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public AuditService(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    public void record(String actor, String action, String target, String reason) {
        jdbc.update("INSERT INTO audit_log(actor,action,target,result,reason,occurred_at) VALUES(?,?,?,'SUCCESS',?,?)",
            actor, action, target, reason, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String,Object> list(LogQuery query) {
        if (query.updatedFrom()!=null && query.updatedTo()!=null && query.updatedFrom().isAfter(query.updatedTo()))
            throw new BusinessException(400,"INVALID_DATE_RANGE","开始日期不能晚于结束日期");
        var where=new StringBuilder(" WHERE 1=1");var params=new ArrayList<Object>();
        if (query.actor()!=null && !query.actor().isBlank()) {where.append(" AND actor=?");params.add(query.actor().trim());}
        if (query.action()!=null && !query.action().isBlank()) {where.append(" AND action=?");params.add(query.action().trim());}
        if (query.updatedFrom()!=null) {where.append(" AND occurred_at>=?");params.add(day(query.updatedFrom()));}
        if (query.updatedTo()!=null) {where.append(" AND occurred_at<?");params.add(day(query.updatedTo().plusDays(1)));}
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log"+where,Long.class,params.toArray()));
        int page=query.page()==null?1:query.page(),size=query.pageSize()==null?20:query.pageSize();
        params.add(size);params.add((long)(page-1)*size);
        var items=jdbc.query("SELECT * FROM audit_log"+where+" ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?",
            (row,index)->new LogView(row.getLong("id"),row.getString("actor"),row.getString("action"),row.getString("target"),row.getString("result"),row.getString("reason"),row.getObject("occurred_at",LocalDateTime.class).toInstant(ZoneOffset.UTC)),params.toArray());
        return Map.of("items",items,"total",total,"page",page,"pageSize",size,"calculatedAt",clock.instant(),"warnings",List.of());
    }
    private static LocalDateTime day(LocalDate date) {
        return LocalDateTime.ofInstant(date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),ZoneOffset.UTC);
    }
}
