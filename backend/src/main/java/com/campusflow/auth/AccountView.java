package com.campusflow.auth;

import java.time.Instant;
import java.time.ZoneOffset;

public record AccountView(long id,String username,String role,boolean enabled,Instant createdAt,long version) {
    static AccountView of(Account account) {
        return new AccountView(account.id,account.username,account.role,account.enabled,account.createdAt.toInstant(ZoneOffset.UTC),account.version);
    }
}
