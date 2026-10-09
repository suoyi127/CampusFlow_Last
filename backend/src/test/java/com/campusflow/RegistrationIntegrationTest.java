package com.campusflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class RegistrationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Test void anonymousRegistrationCreatesOnlyUserAndCanLogin() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
            .content("{\"username\":\"new_student\",\"password\":\"Demo@123456\",\"role\":\"SERVER_ADMIN\",\"enabled\":false}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("role").value("USER"))
            .andExpect(jsonPath("enabled").value(true)).andExpect(jsonPath("passwordHash").doesNotExist());
        var hash=jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE username='new_student'",String.class);
        assertTrue(encoder.matches("Demo@123456",hash));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE actor='new_student' AND action='ACCOUNT_REGISTER'",Integer.class));
        mvc.perform(post("/api/auth/login").with(csrf()).param("username","new_student").param("password","Demo@123456"))
            .andExpect(status().isOk());
    }
    @Test void duplicateAndInvalidInputDoNotCreateAccounts() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
            .content("{\"username\":\"student\",\"password\":\"Demo@123456\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("code").value("USERNAME_EXISTS"));
        for(String body:new String[]{"{}","{\"username\":\"bad name\",\"password\":\"Demo@123456\"}",
            "{\"username\":\"new_student\",\"password\":\"short\"}",
            "{\"username\":\"new_student\",\"password\":\""+"密".repeat(25)+"\"}"}) {
            mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        }
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username='new_student'",Integer.class));
    }
    @Test void registrationRequiresCsrf() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
            .content("{\"username\":\"new_student\",\"password\":\"Demo@123456\"}"))
            .andExpect(status().isForbidden());
    }
}
