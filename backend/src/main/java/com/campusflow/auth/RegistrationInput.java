package com.campusflow.auth;

import jakarta.validation.constraints.*;

public record RegistrationInput(
    @NotNull @Pattern(regexp="[A-Za-z0-9_]{3,64}",message="用户名须为 3—64 个字母、数字或下划线") String username,
    String password) {
    // 请求对象可能进入调试日志，不能输出明文密码。
    @Override public String toString() { return "RegistrationInput[username="+username+", password=<redacted>]"; }
}
