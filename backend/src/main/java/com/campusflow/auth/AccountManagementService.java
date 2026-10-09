package com.campusflow.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campusflow.common.BusinessException;
import com.campusflow.system.AuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class AccountManagementService {
    private final AccountMapper accounts; private final JdbcTemplate jdbc; private final PasswordEncoder encoder;
    private final AuditService audit; private final Clock clock;
    public AccountManagementService(AccountMapper accounts,JdbcTemplate jdbc,PasswordEncoder encoder,AuditService audit,Clock clock) {
        this.accounts=accounts;this.jdbc=jdbc;this.encoder=encoder;this.audit=audit;this.clock=clock;
    }
    public AccountView get(long id) {
        var account=accounts.selectById(id);
        if(account==null)throw new BusinessException(404,"ACCOUNT_NOT_FOUND","账号不存在");
        return AccountView.of(account);
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String,Object> list(AccountQuery query) {
        var where=new QueryWrapper<Account>();
        if(query.username()!=null&&!query.username().isBlank()) where.like("username",query.username().trim());
        if(query.role()!=null)where.eq("role",query.role()); if(query.enabled()!=null)where.eq("enabled",query.enabled());
        long total=accounts.selectCount(where);
        int page=query.page()==null?1:query.page(),size=query.pageSize()==null?20:query.pageSize();
        where.orderByAsc("id").last("LIMIT "+size+" OFFSET "+((long)(page-1)*size));
        return Map.of("items",accounts.selectList(where).stream().map(AccountView::of).toList(),"total",total,"page",page,"pageSize",size,
            "calculatedAt",clock.instant(),"warnings",List.of());
    }
    void lockManagement(String actor) {
        // 所有管理写入先锁固定行，再锁账号，避免两个管理员同时删除最后的管理权限。
        jdbc.queryForObject("SELECT id FROM account_management_lock WHERE id=1 FOR UPDATE",Integer.class);
        // 请求可能等待管理锁；期间操作者已被停用或降权时，不能沿用进入过滤器时的权限。
        var rows=jdbc.queryForList("SELECT enabled,role,version FROM sys_user WHERE username=? FOR UPDATE",actor);
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(rows.isEmpty()||!Boolean.TRUE.equals(rows.get(0).get("enabled"))||!"SERVER_ADMIN".equals(rows.get(0).get("role")))
            throw new BusinessException(401,"SESSION_EXPIRED","账号权限已变化，请重新登录");
        if(authentication!=null&&authentication.getPrincipal() instanceof VersionedUser user&&
            ((Number)rows.get(0).get("version")).longValue()!=user.accountVersion())
            throw new BusinessException(401,"SESSION_EXPIRED","账号权限已变化，请重新登录");
    }
    @Transactional public AccountView create(String actor,AccountCreate input) {
        if(input.password()==null||input.password().length()<8)
            throw new BusinessException(400,"INVALID_PASSWORD","密码至少包含 8 个字符");
        int bytes=input.password().getBytes(StandardCharsets.UTF_8).length;
        if(bytes<8||bytes>72) throw new BusinessException(400,"INVALID_PASSWORD","密码须为 8—72 个 UTF-8 字节");
        lockManagement(actor);
        if(accounts.selectCount(new QueryWrapper<Account>().eq("username",input.username()))>0)
            throw new BusinessException(409,"USERNAME_EXISTS","用户名已存在");
        var account=new Account(); account.username=input.username();account.passwordHash=encoder.encode(input.password());
        account.role=input.role();account.enabled=input.enabled();account.createdAt=LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);account.version=1L;
        accounts.insert(account); audit.record(actor,"ACCOUNT_CREATE",account.id.toString(),"角色="+account.role+"，启用="+account.enabled);
        return AccountView.of(account);
    }
    @Transactional public AccountView update(String actor,long id,AccountUpdate input) {
        lockManagement(actor);
        var account=accounts.lock(id);
        if(account==null)throw new BusinessException(404,"ACCOUNT_NOT_FOUND","账号不存在");
        if(!account.version.equals(input.expectedVersion()))throw new BusinessException(409,"VERSION_CONFLICT","账号已发生变化，请重新加载");
        if(account.role.equals(input.role())&&account.enabled.equals(input.enabled()))return AccountView.of(account);
        if(account.enabled&&account.role.equals("SERVER_ADMIN")&&(!input.enabled()||!input.role().equals("SERVER_ADMIN"))) {
            // 使用锁定读统计，不能读取事务开始时的旧快照，否则并发降权可能各自看到两位管理员。
            var admins=accounts.lockEnabledAdministrators();
            if(admins.size()<=1)throw new BusinessException(409,"LAST_ADMINISTRATOR","至少保留一个启用的服务器管理员");
        }
        account.role=input.role();account.enabled=input.enabled();account.version++;
        accounts.updateById(account);audit.record(actor,"ACCOUNT_UPDATE",account.id.toString(),"角色="+account.role+"，启用="+account.enabled);
        return AccountView.of(account);
    }
}
