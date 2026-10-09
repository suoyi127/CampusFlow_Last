package com.campusflow.review;

import com.campusflow.auth.Account;
import com.campusflow.common.BusinessException;
import com.campusflow.space.SpaceService;
import com.campusflow.system.AuditService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;

@Service
public class ReviewService {
    private static final String VIEW_SQL = "SELECT r.*,u.username,s.name AS space_name,s.enabled AS space_enabled,a.username AS reviewer_name FROM space_review r JOIN sys_user u ON u.id=r.user_id JOIN study_space s ON s.id=r.space_id LEFT JOIN sys_user a ON a.id=r.reviewed_by";
    private final ReviewMapper mapper;
    private final SpaceService spaces;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;
    public ReviewService(ReviewMapper mapper,SpaceService spaces,JdbcTemplate jdbc,AuditService audit,Clock clock) {
        this.mapper=mapper;this.spaces=spaces;this.jdbc=jdbc;this.audit=audit;this.clock=clock;
    }
    private static void requireRole(Account actor,String role) {
        if (!actor.role.equals(role)) throw new BusinessException(403,"FORBIDDEN","当前角色不能执行该操作");
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC); }
    private static void checkVersion(SpaceReview review,long expected) {
        if (review.version != expected) throw new BusinessException(409,"REVIEW_CHANGED","评价已变化，请重新加载后再操作");
    }
    private ReviewView view(long id) {
        return jdbc.queryForObject(VIEW_SQL+" WHERE r.id=?",ReviewService::readView,id);
    }
    private static Instant time(ResultSet row,String field) throws SQLException {
        var value=row.getObject(field,LocalDateTime.class);
        return value==null?null:value.toInstant(ZoneOffset.UTC);
    }
    private static ReviewView readView(ResultSet row,int index) throws SQLException {
        return new ReviewView(row.getLong("id"),row.getLong("user_id"),row.getString("username"),row.getLong("space_id"),row.getString("space_name"),row.getBoolean("space_enabled"),
            row.getInt("environment_score"),row.getInt("facility_score"),row.getString("content"),row.getString("status"),row.getObject("reviewed_by",Long.class),row.getString("reviewer_name"),
            time(row,"reviewed_at"),row.getString("review_reason"),time(row,"updated_at"),row.getLong("version"));
    }
    @Transactional public ReviewView submit(long spaceId,ReviewInput input,Account actor) {
        requireRole(actor,"USER");
        var space=spaces.lockForUpdate(spaceId);
        if (!space.enabled) throw new BusinessException(409,"SPACE_DISABLED","空间已停用，不能提交或修改评价");
        var review=mapper.lockOwned(actor.id,spaceId);
        if (review==null) {
            if (input.expectedVersion()!=0) throw new BusinessException(409,"REVIEW_CHANGED","评价不存在，请重新加载");
            review=new SpaceReview();review.userId=actor.id;review.spaceId=spaceId;review.version=1L;
        } else {
            checkVersion(review,input.expectedVersion());review.version++;
        }
        review.environmentScore=input.environmentScore();review.facilityScore=input.facilityScore();review.content=input.content();
        // 修改已通过内容立即退出公开汇总；最近一次审核信息保留供本人和管理员追溯。
        review.status="PENDING";review.updatedAt=now();
        boolean created=review.id==null;
        if (created) mapper.insert(review);else mapper.updateById(review);
        audit.record(actor.username,created?"REVIEW_SUBMIT":"REVIEW_MODIFY","review:"+review.id,"version="+review.version);
        return view(review.id);
    }
    @Transactional public ReviewView withdraw(long spaceId,long expected,Account actor) {
        requireRole(actor,"USER");spaces.lockForUpdate(spaceId);
        var review=mapper.lockOwned(actor.id,spaceId);
        if (review==null) throw new BusinessException(404,"REVIEW_NOT_FOUND","你尚未评价此空间");
        checkVersion(review,expected);
        if (review.status.equals("WITHDRAWN")) throw new BusinessException(409,"INVALID_REVIEW_STATE","评价已撤回");
        review.status="WITHDRAWN";review.version++;review.updatedAt=now();mapper.updateById(review);
        audit.record(actor.username,"REVIEW_WITHDRAW","review:"+review.id,"用户撤回评价");
        return view(review.id);
    }
    @Transactional public ReviewView decide(long id,ReviewDecision input,Account actor) {
        requireRole(actor,"DATA_ADMIN");
        var found=mapper.selectById(id);
        if (found==null) throw new BusinessException(404,"REVIEW_NOT_FOUND","评价不存在");
        // 所有写入统一先锁空间、再锁评价，避免修改与审核使用相反锁顺序。
        spaces.lockForUpdate(found.spaceId);
        var review=mapper.lock(id);checkVersion(review,input.expectedVersion());
        boolean revoke=input.action().equals("REVOKE");
        if (!(revoke?"APPROVED":"PENDING").equals(review.status)) throw new BusinessException(409,"INVALID_REVIEW_STATE","当前评价状态不允许此审核动作");
        if (!input.action().equals("APPROVE") && input.reason().isBlank()) throw new BusinessException(400,"REASON_REQUIRED","驳回或撤销通过必须填写原因");
        review.status=input.action().equals("APPROVE")?"APPROVED":"REJECTED";
        review.reviewedBy=actor.id;review.reviewedAt=now();review.reviewReason=input.reason().trim();review.updatedAt=now();review.version++;
        mapper.updateById(review);
        audit.record(actor.username,"REVIEW_"+input.action(),"review:"+id,input.reason().trim());
        return view(id);
    }
    @Transactional(readOnly=true) public Map<String,Object> mine(long spaceId,Account actor) {
        requireRole(actor,"USER");var space=spaces.get(spaceId);
        var review=mapper.selectOne(new QueryWrapper<SpaceReview>().eq("user_id",actor.id).eq("space_id",spaceId));
        var result=new HashMap<String,Object>();result.put("review",review==null?null:view(review.id));result.put("spaceEnabled",space.enabled);
        return result;
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String,Object> list(ReviewQuery query,Account actor,boolean admin) {
        requireRole(actor,admin?"DATA_ADMIN":"USER");
        var params=new ArrayList<Object>();var where=new StringBuilder(" WHERE 1=1");
        if (!admin) { where.append(" AND r.user_id=?");params.add(actor.id); }
        if (query.spaceId()!=null) {where.append(" AND r.space_id=?");params.add(query.spaceId());}
        if (admin && query.username()!=null && !query.username().isBlank()) {where.append(" AND u.username=?");params.add(query.username().trim());}
        if (query.status()!=null && !query.status().isBlank()) {
            if (!Set.of("PENDING","APPROVED","REJECTED","WITHDRAWN").contains(query.status())) throw new BusinessException(400,"INVALID_STATUS","评价状态无效");
            where.append(" AND r.status=?");params.add(query.status());
        }
        if (query.updatedFrom()!=null && query.updatedTo()!=null && query.updatedFrom().isAfter(query.updatedTo())) throw new BusinessException(400,"INVALID_DATE_RANGE","开始日期不能晚于结束日期");
        if (query.updatedFrom()!=null) {where.append(" AND r.updated_at>=?");params.add(date(query.updatedFrom()));}
        if (query.updatedTo()!=null) {where.append(" AND r.updated_at<?");params.add(date(query.updatedTo().plusDays(1)));}
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM space_review r JOIN sys_user u ON u.id=r.user_id"+where,Long.class,params.toArray()));
        int page=query.page()==null?1:query.page(),size=query.pageSize()==null?20:query.pageSize();
        params.add(size);params.add((long)(page-1)*size);
        var items=jdbc.query(VIEW_SQL+where+" ORDER BY r.updated_at DESC,r.id DESC LIMIT ? OFFSET ?",ReviewService::readView,params.toArray());
        return page(items,total,page,size);
    }
    private static LocalDateTime date(LocalDate day) {return LocalDateTime.ofInstant(day.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),ZoneOffset.UTC);}
    private Map<String,Object> page(List<?> items,long total,int page,int size) {
        return Map.of("items",items,"total",total,"page",page,"pageSize",size,"calculatedAt",clock.instant(),"warnings",List.of());
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String,Object> published(long spaceId,int page,int size) {
        if (!spaces.get(spaceId).enabled) throw new BusinessException(409,"SPACE_DISABLED","空间已停用");
        var summary=summary(spaceId);
        var items=jdbc.query("SELECT id,user_id,environment_score,facility_score,content,updated_at,reviewed_at FROM space_review WHERE space_id=? AND status='APPROVED' ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?",
            (row,index)->new PublicReview(row.getLong("id"),"用户 #"+row.getLong("user_id"),row.getInt("environment_score"),row.getInt("facility_score"),row.getString("content"),time(row,"updated_at"),time(row,"reviewed_at")),spaceId,size,(long)(page-1)*size);
        var result=new HashMap<>(page(items,summary.count(),page,size));result.put("summary",summary);return result;
    }
    @Transactional(readOnly=true) public ReviewSummary summary(long spaceId) {
        return jdbc.queryForObject("SELECT COUNT(*) AS total,AVG(environment_score) AS environment_average,AVG(facility_score) AS facility_average FROM space_review WHERE space_id=? AND status='APPROVED'",
            (row,index)->readSummary(row),spaceId);
    }
    private static ReviewSummary readSummary(ResultSet row) throws SQLException {
        long count=row.getLong("total");return new ReviewSummary(count,count==0?null:row.getDouble("environment_average"),count==0?null:row.getDouble("facility_average"));
    }
    @Transactional(readOnly=true) public Map<Long,ReviewSummary> summaries() {
        var result=new HashMap<Long,ReviewSummary>();
        jdbc.query("SELECT space_id,COUNT(*) AS total,AVG(environment_score) AS environment_average,AVG(facility_score) AS facility_average FROM space_review WHERE status='APPROVED' GROUP BY space_id",
            (org.springframework.jdbc.core.RowCallbackHandler)row->result.put(row.getLong("space_id"),readSummary(row)));
        return result;
    }
}
