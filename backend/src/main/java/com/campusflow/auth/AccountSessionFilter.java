package com.campusflow.auth;

import com.campusflow.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class AccountSessionFilter extends OncePerRequestFilter {
    public static final String VERSION="campusflow.accountVersion";
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public AccountSessionFilter(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null&&auth.isAuthenticated()&&!(auth instanceof AnonymousAuthenticationToken)) {
            var session=request.getSession(false); var version=session==null?null:session.getAttribute(VERSION);
            // 不仅检查 enabled：版本在禁用、再启用后仍递增，旧会话不能重新获得权限。
            boolean valid=version instanceof Long && Boolean.TRUE.equals(jdbc.query("SELECT enabled,version FROM sys_user WHERE username=?",
                result -> result.next()&&result.getBoolean("enabled")&&result.getLong("version")==((Long)version),auth.getName()));
            if(!valid) {
                SecurityContextHolder.clearContext(); if(session!=null)session.invalidate();
                response.setStatus(401);response.setContentType("application/json;charset=UTF-8");
                json.writeValue(response.getWriter(),ApiError.of("SESSION_EXPIRED","账号权限已变化，请重新登录"));return;
            }
        }
        chain.doFilter(request,response);
    }
}
