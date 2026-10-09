package com.campusflow.auth;

import jakarta.validation.constraints.*;

public record AccountCreate(
    @NotNull @Pattern(regexp="[A-Za-z0-9_]{3,64}",message="用户名须为 3—64 个字母、数字或下划线") String username,
    String password,
    @NotNull @Pattern(regexp="USER|DATA_ADMIN|SERVER_ADMIN") String role,
    @NotNull Boolean enabled) {
    // Spring 的调试日志会调用请求对象 toString，密码不能出现在日志或校验异常中。
    @Override public String toString() {
        return "AccountCreate[username="+username+", password=<redacted>, role="+role+", enabled="+enabled+"]";
    }
}
