package com.campusflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InitialIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

    MockHttpSession login(String name) throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf()).param("username", name)
            .param("password", "Demo@123456")).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test void anonymousCannotReadSpaces() throws Exception {
        mvc.perform(get("/api/spaces").param("latitude","31.2304").param("longitude","121.4737")).andExpect(status().isUnauthorized());
    }

    @Test void loginReadsTenSpacesAndLogoutInvalidatesSession() throws Exception {
        var session = login("student");
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("role").value("USER")).andExpect(jsonPath("passwordHash").doesNotExist());
        mvc.perform(get("/api/spaces").param("latitude","31.2304").param("longitude","121.4737").param("openOnly", "false").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("items", hasSize(10)));
        mvc.perform(post("/api/auth/logout").with(csrf()).session(session)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(session.isInvalid());
    }

    @Test void rolesDoNotInheritAndWritesRequireCsrf() throws Exception {
        var user = login("student");
        var server = login("server_admin");
        var data = login("data_admin");
        mvc.perform(get("/api/data/spaces").session(user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/data/spaces").session(server)).andExpect(status().isForbidden());
        mvc.perform(get("/api/system/overview").session(data)).andExpect(status().isForbidden());
        mvc.perform(get("/api/data/spaces").session(data)).andExpect(status().isOk());
        mvc.perform(post("/api/system/simulation/pause").session(server)).andExpect(status().isForbidden());
    }

    @Test void badPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "student").param("password", "wrong"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("LOGIN_FAILED"));
    }

    @Test void unboundSpaceHasUnknownHardwareReadings() throws Exception {
        mvc.perform(get("/api/spaces/6").param("latitude","31.2304").param("longitude","121.4737").session(login("student"))).andExpect(status().isOk())
            .andExpect(jsonPath("status.source").value("HARDWARE"))
            .andExpect(jsonPath("status.peopleState").value("MISSING"))
            .andExpect(jsonPath("status.currentPeople").isEmpty())
            .andExpect(jsonPath("status.noiseDb").isEmpty());
    }

    @Test void facilityConditionsAreAndAndInvalidQueriesAreRejected() throws Exception {
        var session = login("student");
        var result = mvc.perform(get("/api/spaces").param("latitude","31.2304").param("longitude","121.4737").session(session).param("openOnly","false")
            .param("facilities","AC","POWER","WIFI").param("sort","DISTANCE"))
            .andExpect(status().isOk()).andReturn();
        double last = -1;
        var items = json.readTree(result.getResponse().getContentAsString()).get("items");
        org.junit.jupiter.api.Assertions.assertFalse(items.isEmpty());
        for (var card : items) {
            String facilities = card.get("space").get("facilities").asText();
            org.junit.jupiter.api.Assertions.assertTrue(facilities.contains("AC") && facilities.contains("POWER") && facilities.contains("WIFI"));
            double distance = card.get("distanceMeters").asDouble();
            org.junit.jupiter.api.Assertions.assertTrue(distance >= last); last = distance;
        }
        mvc.perform(get("/api/spaces").param("latitude","31.2304").param("longitude","121.4737").session(session).param("maxOccupancy","1.5")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/spaces").param("latitude","31.2304").param("longitude","121.4737").session(session).param("sort","unknown")).andExpect(status().isBadRequest());
    }

    @Test void removedSimulationControlsAreUnavailable() throws Exception {
        mvc.perform(post("/api/system/simulation/resume").session(login("server_admin")).with(csrf()))
            .andExpect(status().isNotFound());
    }
}
