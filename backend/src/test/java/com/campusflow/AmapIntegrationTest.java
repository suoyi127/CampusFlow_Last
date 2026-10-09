package com.campusflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AmapIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    MockHttpSession login(String name) throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).param("username",name)
            .param("password","Demo@123456")).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    @Test void proxyRequiresLoginAndRejectsUnsupportedPaths() throws Exception {
        mvc.perform(get("/api/amap/_AMapService/v3/place/text")).andExpect(status().isUnauthorized());
        var session=login("student");
        mvc.perform(get("/api/amap/_AMapService/anything").session(session)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/amap/_AMapService/v3/place/text").session(session)).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("code").value("MAP_NOT_CONFIGURED"));
    }
    @Test void unknownCoordinatesAreExcludedFromRecommendations() throws Exception {
        jdbc.update("UPDATE study_space SET coordinate_system='UNKNOWN' WHERE id=6");
        var user=login("student");
        mvc.perform(get("/api/spaces/6").session(user)).andExpect(status().isConflict())
            .andExpect(jsonPath("code").value("COORDINATES_UNCONFIRMED"));
        mvc.perform(get("/api/spaces").param("openOnly","false").session(user)).andExpect(status().isOk())
            .andExpect(jsonPath("total").value(9));
    }
    @Test void migrationRecognizesTenSyntheticDemoCoordinates() {
        org.junit.jupiter.api.Assertions.assertEquals(10,jdbc.queryForObject("SELECT COUNT(*) FROM study_space WHERE coordinate_system='GCJ02'",Integer.class));
    }
    @Test void administratorMustUseGcj02AndCanConfirmUnknownCoordinates() throws Exception {
        jdbc.update("UPDATE study_space SET coordinate_system='UNKNOWN' WHERE id=6");
        var admin=login("data_admin");
        var body=json.createObjectNode().put("name","湖畔学习亭").put("type","OUTDOOR")
            .put("address","测试地址").put("latitude",31.227).put("longitude",121.471)
            .put("capacity",16).put("openTime","00:00").put("closeTime","23:59")
            .put("allDay",true).put("description","").put("enabled",true).put("coordinateSystem","WGS84");
        body.putArray("openDays").add(1);body.putArray("facilities").add("SEAT");
        mvc.perform(put("/api/data/spaces/6").session(admin).with(csrf()).contentType("application/json")
            .content(body.toString())).andExpect(status().isBadRequest());
        body.remove("coordinateSystem");
        mvc.perform(put("/api/data/spaces/6").session(admin).with(csrf()).contentType("application/json")
            .content(body.toString())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("code").value("COORDINATES_UNCONFIRMED"));
        body.put("coordinateSystem","GCJ02");
        mvc.perform(put("/api/data/spaces/6").session(admin).with(csrf()).contentType("application/json")
            .content(body.toString())).andExpect(status().isOk()).andExpect(jsonPath("coordinateSystem").value("GCJ02"));
        mvc.perform(get("/api/spaces/6").session(login("student"))).andExpect(status().isOk());
    }
}
