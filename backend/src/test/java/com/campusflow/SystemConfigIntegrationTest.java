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
    @Autowired com.campusflow.simulation.SimulationService simulation;
    @Autowired java.time.Clock clock;
    MockHttpSession login(String username) throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).param("username",username).param("password","Demo@123456"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    String input(long version,int sim,int valid,int window,double distance,double quiet,double free,double facility) throws Exception {
        return json.writeValueAsString(Map.of("expectedVersion",version,"simulationSeconds",sim,"validitySeconds",valid,"noiseWindowSeconds",window,
            "distanceWeight",distance,"quietWeight",quiet,"freeWeight",free,"facilityWeight",facility));
    }
    @Test void saveChecksVersionAndChangesRuntimeBehaviorWithAudit() throws Exception {
        var admin=login("server_admin");var current=configs.current();
        var space=spaces.get(6);var status=statuses.current(space);assertNotNull(status.peopleUpdatedAt());
        mvc.perform(put("/api/system/config").session(admin).with(csrf()).contentType("application/json")
            .content(input(current.version(),3,60,90,0,0,0,1))).andExpect(status().isOk())
            .andExpect(jsonPath("version").value(current.version()+1));
        assertEquals(3,configs.current().simulationSeconds());
        assertEquals("VALID",statuses.current(space,status.peopleUpdatedAt().plusSeconds(45)).peopleState());
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
        for (String body:new String[]{input(version,1,30,60,.3,.3,.25,.15),input(version,10,15,60,.3,.3,.25,.15),
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
    @Test void configuredNoiseWindowIncludesOlderValidSamples() throws Exception {
        // DATETIME(6) 存储精度低于 Instant；固定样本时间不能因舍入而落在查询时刻之后。
        var now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);long run=simulation.runId();long device=jdbc.queryForObject("SELECT id FROM noise_device WHERE space_id=6",Long.class);
        jdbc.update("DELETE FROM noise_sample WHERE space_id=6");
        for (var sample:Map.of(now.minusSeconds(80),90.0,now,45.0).entrySet()) {
            jdbc.update("INSERT INTO noise_sample(run_id,device_id,space_id,noise_db,sampled_at,valid) VALUES(?,?,6,?,?,TRUE)",run,device,sample.getValue(),java.time.LocalDateTime.ofInstant(sample.getKey(),java.time.ZoneOffset.UTC));
        }
        assertEquals(45.0,statuses.current(spaces.get(6),now).typicalNoiseDb());
        var config=configs.current();
        mvc.perform(put("/api/system/config").session(login("server_admin")).with(csrf()).contentType("application/json")
            .content(input(config.version(),5,30,90,.3,.3,.25,.15))).andExpect(status().isOk());
        assertEquals(67.5,statuses.current(spaces.get(6),now).typicalNoiseDb());
    }
}
