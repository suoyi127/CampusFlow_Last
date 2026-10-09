package com.campusflow.auth;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;

public class VersionedUser extends User {
    private final long accountVersion;
    public VersionedUser(Account account) {
        super(account.username,account.passwordHash,account.enabled,true,true,true,List.of(new SimpleGrantedAuthority("ROLE_"+account.role)));
        this.accountVersion=account.version;
    }
    public long accountVersion(){return accountVersion;}
}
