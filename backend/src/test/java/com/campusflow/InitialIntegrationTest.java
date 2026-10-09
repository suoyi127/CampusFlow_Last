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
    @Autowired com.campusflow.simulation.SimulationService simulation;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

    MockHttpSession login(String name) throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf()).param("username", name)
            .param("password", "Demo@123456")).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test void anonymousCannotReadSpaces() throws Exception {
        mvc.perform(get("/api/spaces")).andExpect(status().isUnauthorized());
    }

    @Test void loginReadsTenSpacesAndLogoutInvalidatesSession() throws Exception {
        var session = login("student");
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("role").value("USER")).andExpect(jsonPath("passwordHash").doesNotExist());
        mvc.perform(get("/api/spaces").param("openOnly", "false").session(session))
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

    @Test void simulationProducesBoundedPeopleAndMedianWithSeparateTimes() throws Exception {
        simulation.tick();
        Integer violations = jdbc.queryForObject("SELECT COUNT(*) FROM space_snapshot p JOIN study_space s ON s.id=p.space_id WHERE p.current_people < 0 OR p.current_people > s.capacity", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(0, violations);
        var session = login("student");
        mvc.perform(get("/api/spaces/6").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("status.peopleState").value("VALID"))
            .andExpect(jsonPath("status.noiseState").value("VALID"))
            .andExpect(jsonPath("status.peopleUpdatedAt").isNotEmpty())
            .andExpect(jsonPath("status.noiseUpdatedAt").isNotEmpty());
    }

    @Test void facilityConditionsAreAndAndInvalidQueriesAreRejected() throws Exception {
        var session = login("student");
        var result = mvc.perform(get("/api/spaces").session(session).param("openOnly","false")
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
        mvc.perform(get("/api/spaces").session(session).param("maxOccupancy","1.5")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/spaces").session(session).param("sort","unknown")).andExpect(status().isBadRequest());
    }

    @Test void capacityCannotBeLoweredBelowPresentVisitsAndDisabledSpaceIsHidden() throws Exception {
        var session = login("data_admin");
        var space = jdbc.queryForMap("SELECT * FROM study_space WHERE id=8");
        Long people = jdbc.queryForObject("SELECT COUNT(*) FROM sim_visit WHERE space_id=8 AND checked_out_at IS NULL",Long.class);
        org.junit.jupiter.api.Assertions.assertTrue(people > 1);
        var body = new java.util.HashMap<String,Object>();
        body.put("name",space.get("name")); body.put("type",space.get("type")); body.put("address",space.get("address"));
        body.put("latitude",space.get("latitude")); body.put("longitude",space.get("longitude")); body.put("capacity",1);
        body.put("openTime",space.get("open_time")); body.put("closeTime",space.get("close_time"));
        body.put("openDays", java.util.List.of(1,2,3,4,5,6,7)); body.put("allDay",true);
        body.put("facilities",java.util.List.of("AC","SEAT","POWER","WIFI")); body.put("description",""); body.put("enabled",true);
        mvc.perform(put("/api/data/spaces/8").with(csrf()).session(session).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isConflict()).andExpect(jsonPath("code").value("CAPACITY_TOO_SMALL"));
        body.put("capacity",space.get("capacity")); body.put("enabled",false);
        try {
            simulation.control(false,"server_admin");
            mvc.perform(put("/api/data/spaces/8").with(csrf()).session(session).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk());
            mvc.perform(get("/api/spaces/8").session(session)).andExpect(status().isConflict());
            org.junit.jupiter.api.Assertions.assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sim_visit WHERE space_id=8 AND checked_out_at IS NULL",Long.class));
        } finally { jdbc.update("UPDATE study_space SET enabled=TRUE WHERE id=8"); simulation.control(true,"server_admin"); }
    }
}
