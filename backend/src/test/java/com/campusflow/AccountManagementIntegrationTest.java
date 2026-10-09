package com.campusflow;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AccountManagementIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    MockHttpSession login(String username) throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).param("username",username).param("password","Demo@123456"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    JsonNode create(MockHttpSession admin,String username,String role) throws Exception {
        return json.readTree(mvc.perform(post("/api/system/accounts").session(admin).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("username",username,"password","Demo@123456","role",role,"enabled",true))))
            .andExpect(status().isOk()).andExpect(jsonPath("passwordHash").doesNotExist()).andReturn().getResponse().getContentAsString());
    }
    JsonNode update(MockHttpSession admin,long id,String role,boolean enabled,long version) throws Exception {
        return json.readTree(mvc.perform(put("/api/system/accounts/"+id).session(admin).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("role",role,"enabled",enabled,"expectedVersion",version))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void managesAccountsAndAuditsWithoutPasswords() throws Exception {
        var admin=login("server_admin"); var row=create(admin,"managed_user","USER");
        mvc.perform(get("/api/system/accounts/"+row.get("id")).session(admin)).andExpect(status().isOk())
            .andExpect(jsonPath("username").value("managed_user")).andExpect(jsonPath("passwordHash").doesNotExist());
        mvc.perform(get("/api/system/accounts/999999").session(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/system/accounts").session(admin).param("username","managed").param("role","USER").param("enabled","true"))
            .andExpect(status().isOk()).andExpect(jsonPath("total").value(1)).andExpect(jsonPath("items[0].username").value("managed_user"))
            .andExpect(jsonPath("items[0].passwordHash").doesNotExist()).andExpect(jsonPath("items[0].createdAt",endsWith("Z")));
        update(admin,row.get("id").asLong(),"DATA_ADMIN",false,1);
        mvc.perform(put("/api/system/accounts/"+row.get("id")).session(admin).with(csrf()).contentType("application/json")
            .content("{\"role\":\"USER\",\"enabled\":true,\"expectedVersion\":1}")).andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action IN ('ACCOUNT_CREATE','ACCOUNT_UPDATE') AND target=?",Integer.class,row.get("id").asText()));
        org.junit.jupiter.api.Assertions.assertTrue(jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id=?",String.class,row.get("id").asLong()).startsWith("$2"));
    }
    @Test void lastAdministratorCannotBeDisabledOrDemoted() throws Exception {
        var admin=login("server_admin"); long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE username='server_admin'",Long.class);
        mvc.perform(put("/api/system/accounts/"+id).session(admin).with(csrf()).contentType("application/json")
            .content("{\"role\":\"SERVER_ADMIN\",\"enabled\":false,\"expectedVersion\":1}")).andExpect(status().isConflict());
        mvc.perform(put("/api/system/accounts/"+id).session(admin).with(csrf()).contentType("application/json")
            .content("{\"role\":\"USER\",\"enabled\":true,\"expectedVersion\":1}")).andExpect(status().isConflict());
        create(admin,"second_admin","SERVER_ADMIN"); update(admin,id,"USER",true,1);
        mvc.perform(get("/api/system/accounts").session(admin)).andExpect(status().isUnauthorized());
    }
    @Test void disablingAndReenablingDoesNotReviveOldSession() throws Exception {
        var admin=login("server_admin"); var row=create(admin,"session_user","USER"); var user=login("session_user");
        update(admin,row.get("id").asLong(),"USER",false,1);
        update(admin,row.get("id").asLong(),"USER",true,2);
        mvc.perform(get("/api/auth/me").session(user)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").session(login("session_user"))).andExpect(status().isOk());
    }
    @Test void rejectsInvalidDuplicateForbiddenAndMissingCsrf() throws Exception {
        var admin=login("server_admin");
        mvc.perform(get("/api/system/accounts").session(login("student"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/system/accounts").session(admin).contentType("application/json").content("{}")) .andExpect(status().isForbidden());
        create(admin,"duplicate_user","USER");
        mvc.perform(post("/api/system/accounts").session(admin).with(csrf()).contentType("application/json")
            .content("{\"username\":\"duplicate_user\",\"password\":\"Demo@123456\",\"role\":\"USER\",\"enabled\":true}")).andExpect(status().isConflict());
        mvc.perform(post("/api/system/accounts").session(admin).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("username","valid_name","password","汉".repeat(25),"role","USER","enabled",true)))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/system/accounts").session(admin).with(csrf()).contentType("application/json")
            .content("{\"username\":\"valid_name\",\"password\":\"short\",\"role\":\"USER\",\"enabled\":true}")).andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertFalse(new com.campusflow.auth.AccountCreate("sample_user","secret123","USER",true).toString().contains("secret123"));
    }
}
