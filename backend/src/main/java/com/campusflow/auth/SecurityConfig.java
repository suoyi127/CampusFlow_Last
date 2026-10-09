package com.campusflow.auth;

import com.campusflow.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SecurityConfig {
    private final ObjectMapper json;
    public SecurityConfig(ObjectMapper json) { this.json = json; }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    void write(HttpServletResponse response, int status, Object value) throws java.io.IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getWriter(), value);
    }
    @Bean SecurityFilterChain security(HttpSecurity http,JdbcTemplate jdbc) throws Exception {
        // 在 CSRF 与权限检查前核对会话，停用和降权统一得到 401。
        http.addFilterBefore(new AccountSessionFilter(jdbc,json),CsrfFilter.class);
        // 三角色互不继承，不能因为是服务器管理员就获得业务数据修改权限。
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/csrf", "/api/auth/login", "/api/auth/register").permitAll()
            .requestMatchers("/api/data/**").hasRole("DATA_ADMIN")
            .requestMatchers("/api/system/**").hasRole("SERVER_ADMIN")
            .requestMatchers("/api/user/**").hasRole("USER")
            .requestMatchers("/api/**").authenticated()
            .anyRequest().denyAll());
        http.exceptionHandling(errors -> errors
            .authenticationEntryPoint((req, res, e) -> write(res, 401, ApiError.of("UNAUTHENTICATED", "请先登录")))
            .accessDeniedHandler((req, res, e) -> write(res, 403, ApiError.of("FORBIDDEN", "权限不足或安全令牌已失效"))));
        http.formLogin(login -> login.loginProcessingUrl("/api/auth/login")
            .successHandler((req, res, auth) -> {
                // 保存认证时读取的版本，不能再查最新版本给旧角色会话续权。
                req.getSession().setAttribute(AccountSessionFilter.VERSION,((VersionedUser)auth.getPrincipal()).accountVersion());
                write(res,200,java.util.Map.of("username",auth.getName()));
            })
            .failureHandler((req, res, e) -> write(res, 401, ApiError.of("LOGIN_FAILED", "账号、密码不正确或账号已停用"))));
        http.logout(logout -> logout.logoutUrl("/api/auth/logout")
            .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)));
        http.requestCache(cache -> cache.disable());
        return http.build();
    }
}
