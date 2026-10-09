package com.campusflow.hardware;

import com.campusflow.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
public class HardwareSecurity {
    @Bean @Order(1) SecurityFilterChain hardwareFilterChain(HttpSecurity http,ObjectMapper json,
        @Value("${campusflow.checkin.http-token:}") String token) throws Exception {
        // 机器接口单独使用 Bearer 认证，不读取 Cookie，也不放宽其他接口的 CSRF。
        http.securityMatcher("/api/hardware/**").csrf(csrf->csrf.disable())
            .sessionManagement(session->session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(cache->cache.disable())
            .authorizeHttpRequests(auth->auth.requestMatchers(HttpMethod.POST,"/api/hardware/events","/api/hardware/telemetry").permitAll().anyRequest().denyAll());
        http.addFilterBefore(new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
                String supplied=request.getHeader("Authorization");
                boolean valid=!token.isBlank() && supplied!=null && MessageDigest.isEqual(("Bearer "+token).getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8));
                if(!valid) {
                    response.setStatus(token.isBlank()?503:401);response.setContentType("application/json;charset=UTF-8");
                    json.writeValue(response.getWriter(),ApiError.of(token.isBlank()?"HARDWARE_HTTP_DISABLED":"INVALID_DEVICE_TOKEN",token.isBlank()?"尚未配置设备HTTP接收令牌":"设备接收令牌无效"));return;
                }
                chain.doFilter(request,response);
            }
        },UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
