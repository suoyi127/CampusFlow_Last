package com.campusflow.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.*;

@Component
@Order(20)
@Profile("!test")
public class AccountBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;
    private final Clock clock;

    public AccountBootstrap(JdbcTemplate jdbc, PasswordEncoder encoder, Clock clock,
        @Value("${CF_ADMIN_USERNAME:}") String username,
        @Value("${CF_ADMIN_PASSWORD:}") String password) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.clock = clock;
        this.username = username;
        this.password = password;
    }

    @Override @Transactional public void run(ApplicationArguments args) {
        // 只停用仍使用公开演示口令、角色也未改变的旧样例；不删除账号或重置真实密码。
        String[][] samples = {{"student", "USER"}, {"data_admin", "DATA_ADMIN"}, {"server_admin", "SERVER_ADMIN"}};
        for (var sample : samples) {
            var rows = jdbc.queryForList("SELECT id,password_hash,role FROM sys_user WHERE username=?", sample[0]);
            if (!rows.isEmpty() && sample[1].equals(rows.getFirst().get("role"))
                && encoder.matches("Demo@123456", (String) rows.getFirst().get("password_hash"))) {
                jdbc.update("UPDATE sys_user SET enabled=FALSE,data_origin='LEGACY_SAMPLE',version=version+1 WHERE id=? AND data_origin<>'LEGACY_SAMPLE'", rows.getFirst().get("id"));
            }
        }
        if (username.isBlank() && password.isBlank()) return;
        if (!username.matches("[A-Za-z0-9_]{3,64}") || password.length() < 8
            || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("首次管理员需配置有效的 CF_ADMIN_USERNAME 与 CF_ADMIN_PASSWORD");
        // 显式配置只引导空管理员库，不覆盖已有账号及其权限。
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE role='SERVER_ADMIN' AND enabled=TRUE", Long.class) > 0) return;
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username=?", Long.class, username) > 0)
            throw new IllegalStateException("首次管理员用户名已存在，请选择新的专用账号");
        jdbc.update("INSERT INTO sys_user(username,password_hash,role,enabled,created_at) VALUES(?,?,'SERVER_ADMIN',TRUE,?)",
            username, encoder.encode(password), LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }
}
