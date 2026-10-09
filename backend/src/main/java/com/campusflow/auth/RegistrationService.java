package com.campusflow.auth;

import com.campusflow.common.BusinessException;
import com.campusflow.system.AuditService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.*;

@Service
public class RegistrationService {
    private final AccountMapper accounts;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final Clock clock;
    public RegistrationService(AccountMapper accounts,PasswordEncoder encoder,AuditService audit,Clock clock) {
        this.accounts=accounts;this.encoder=encoder;this.audit=audit;this.clock=clock;
    }
    @Transactional public AccountView register(RegistrationInput input) {
        if(input.password()==null||input.password().length()<8||input.password().getBytes(StandardCharsets.UTF_8).length>72)
            throw new BusinessException(400,"INVALID_PASSWORD","密码至少 8 个字符且最多 72 个 UTF-8 字节");
        var account=new Account();
        account.username=input.username();account.passwordHash=encoder.encode(input.password());
        // 公开注册不能采用客户端指定的角色或启停状态。
        account.role="USER";account.enabled=true;account.version=1L;
        account.createdAt=LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);
        try { accounts.insert(account); }
        catch(DuplicateKeyException exception) {
            // 由唯一索引处理并发重名，避免先查询再写入的竞争窗口。
            throw new BusinessException(409,"USERNAME_EXISTS","用户名已存在");
        }
        audit.record(account.username,"ACCOUNT_REGISTER",account.id.toString(),"普通用户自行注册");
        return AccountView.of(account);
    }
}
