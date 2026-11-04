package com.campusflow;

import com.campusflow.system.ConfigService;
import com.campusflow.status.StatusService;
import com.campusflow.space.SpaceService;
import com.campusflow.recommendation.RecommendationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.time.Instant;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class SystemConfigIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ConfigService configs;
    @Autowired JdbcTemplate jdbc;
    @Autowired SpaceService spaces;
    @Autowired StatusService statuses;
    @Autowired RecommendationService recommendations;
    @Autowired java.time.Clock clock;
    MockHttpSession login(String username) throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).param("username",username).param("password","Demo@123456"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    String input(long version,int sim,int valid,int window,double distance,double quiet,double free,double facility) throws Exception {
        return json.writeValueAsString(Map.of("expectedVersion",version,"validitySeconds",valid,"noiseWindowSeconds",window,
            "distanceWeight",distance,"quietWeight",quiet,"freeWeight",free,"facilityWeight",facility));
    }
    @Test void saveChecksVersionAndChangesRuntimeBehaviorWithAudit() throws Exception {
        var admin=login("server_admin");var current=configs.current();
        var space=spaces.get(6);var status=statuses.current(space);assertNull(status.peopleUpdatedAt());
        mvc.perform(put("/api/system/config").session(admin).with(csrf()).contentType("application/json")
            .content(input(current.version(),3,60,90,0,0,0,1))).andExpect(status().isOk())
            .andExpect(jsonPath("version").value(current.version()+1));
        assertEquals("MISSING",statuses.current(space).peopleState());
        assertEquals(60,statuses.current(space).validitySeconds());
        var card=recommendations.detail(6,space.latitude,space.longitude);
        assertEquals(space.facilities.split(",").length*25.0,card.score(),0.001);
        mvc.perform(put("/api/system/config").session(admin).with(csrf()).contentType("application/json")
            .content(input(current.version(),3,60,90,.3,.3,.25,.15))).andExpect(status().isConflict());
        mvc.perform(get("/api/system/logs").session(admin).param("action","CONFIG_UPDATE"))
            .andExpect(status().isOk()).andExpect(jsonPath("total").value(1))
            .andExpect(jsonPath("items[0].actor").value("server_admin"));
    }
    @Test void invalidRangesRelationsWeightsAndPermissionsAreRejected() throws Exception {
        var admin=login("server_admin");long version=configs.current().version();
        for (String body:new String[]{input(version,5,4,60,.3,.3,.25,.15),input(version,5,301,600,.3,.3,.25,.15),
            input(version,5,30,20,.3,.3,.25,.15),input(version,5,30,60,.4,.3,.25,.15)}) {
            mvc.perform(put("/api/system/config").session(admin).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/system/config").session(login("student"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/system/logs").session(login("data_admin"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/system/config").session(admin).contentType("application/json").content(input(version,5,30,60,.3,.3,.25,.15))).andExpect(status().isForbidden());
        assertEquals(version,configs.current().version());
    }
    @Test void auditPaginationFiltersAndShanghaiDatesAreChecked() throws Exception {
        var admin=login("server_admin");
        jdbc.update("INSERT INTO audit_log(actor,action,target,result,reason,occurred_at) VALUES('filter_actor','FILTER_TEST','x','SUCCESS','test','2026-10-08 16:00:00')");
        mvc.perform(get("/api/system/logs").session(admin).param("actor","filter_actor").param("updatedFrom","2026-10-09").param("updatedTo","2026-10-09").param("pageSize","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("total").value(1)).andExpect(jsonPath("items[0].occurredAt").value("2026-10-08T16:00:00Z"));
        mvc.perform(get("/api/system/logs").session(admin).param("pageSize","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/system/logs").session(admin).param("updatedFrom","2026-10-10").param("updatedTo","2026-10-09")).andExpect(status().isBadRequest());
    }
    @Test void noSimulationPeriodIsExposedOrRequired() throws Exception {
        var admin=login("server_admin");
        mvc.perform(get("/api/system/config").session(admin)).andExpect(status().isOk())
            .andExpect(jsonPath("simulationSeconds").doesNotExist());
    }
}
