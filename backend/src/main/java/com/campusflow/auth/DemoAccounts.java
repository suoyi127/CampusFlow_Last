package com.campusflow.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@ConditionalOnProperty(name = "campusflow.demo-enabled", havingValue = "true")
public class DemoAccounts implements ApplicationRunner {
    private final AccountMapper mapper;
    private final AccountService service;
    private final PasswordEncoder encoder;
    public DemoAccounts(AccountMapper mapper, AccountService service, PasswordEncoder encoder) {
        this.mapper = mapper; this.service = service; this.encoder = encoder;
    }
    @Override public void run(ApplicationArguments args) {
        String[][] accounts = {{"student", "USER"}, {"data_admin", "DATA_ADMIN"}, {"server_admin", "SERVER_ADMIN"}};
        for (var entry : accounts) {
            if (service.find(entry[0]) != null) continue;
            var account = new Account();
            account.username = entry[0]; account.role = entry[1]; account.enabled = true;
            account.passwordHash = encoder.encode("Demo@123456");
            account.createdAt = LocalDateTime.now(ZoneOffset.UTC);
            mapper.insert(account);
        }
    }
}
