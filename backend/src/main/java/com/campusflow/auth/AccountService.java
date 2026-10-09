package com.campusflow.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campusflow.common.BusinessException;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class AccountService implements UserDetailsService {
    private final AccountMapper mapper;
    public AccountService(AccountMapper mapper) { this.mapper = mapper; }
    public Account find(String username) {
        return mapper.selectOne(new QueryWrapper<Account>().eq("username", username));
    }
    @Override public UserDetails loadUserByUsername(String username) {
        var account = find(username);
        if (account == null) throw new UsernameNotFoundException("账号不存在");
        return User.withUsername(account.username).password(account.passwordHash)
            .roles(account.role).disabled(!account.enabled).build();
    }
    public Account current(String username) {
        var account = find(username);
        if (account == null || !account.enabled) throw new BusinessException(401, "ACCOUNT_DISABLED", "账号已停用");
        return account;
    }
    public long count() { return mapper.selectCount(null); }
}
