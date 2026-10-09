package com.campusflow;

import com.campusflow.simulation.SimulationService;
import com.campusflow.space.SpaceMapper;
import com.campusflow.status.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties="spring.datasource.url=${CF_SIM_TEST_DB_URL:jdbc:h2:mem:simulation_management;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SimulationManagementTest.TimeConfig.class)
class SimulationManagementTest {
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-09T02:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
        void advance(long seconds) { now = now.plusSeconds(seconds); }
    }
    @TestConfiguration static class TimeConfig {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }
    @Autowired MutableClock clock;
    @Autowired SimulationService simulation;
    @Autowired InspectionService inspection;
    @Autowired StatusService statuses;
    @Autowired SpaceMapper spaces;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired com.campusflow.system.ConfigService configs;

    @BeforeEach void prepare() {
        clock.now = Instant.parse("2026-10-09T02:00:00Z");
        jdbc.update("UPDATE study_space SET enabled=TRUE,all_day=TRUE");
        jdbc.update("UPDATE study_space SET capacity=16 WHERE id=6");
        configs.update(new com.campusflow.system.ConfigInput(configs.current().version(),5,30,60,.3,.3,.25,.15),"test");
        simulation.reset("test"); simulation.control(true,"test");
    }
    org.springframework.mock.web.MockHttpSession login(String username) throws Exception {
        // 整合账号版本校验后，接口测试也使用真实登录会话，不能伪造通用 User principal。
        return (org.springframework.mock.web.MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf())
            .param("username",username).param("password","Demo@123456")).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    SpaceStatus current() { return statuses.current(spaces.selectById(6)); }
    long count(String table) { return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM " + table,Long.class)); }
    long latest(String table) { return Objects.requireNonNull(jdbc.queryForObject("SELECT MAX(id) FROM " + table + " WHERE run_id=? AND space_id=6",Long.class,simulation.runId())); }

    @Test void markingFallsBackToRecentValidSnapshotAndRestoresWithoutChangingVisits() {
        var initial = current(); long old = latest("space_snapshot");
        clock.advance(5); simulation.tick(); long newest = latest("space_snapshot");
        long visits = simulation.currentPeople(6);
        inspection.validity(InspectionService.Kind.PEOPLE,newest,false,"异常人数","data_admin");
        assertEquals(old,current().snapshotVersion()); assertEquals(initial.currentPeople(),current().currentPeople());
        assertEquals(visits,simulation.currentPeople(6));
        inspection.validity(InspectionService.Kind.PEOPLE,newest,true,"人工确认恢复","data_admin");
        assertEquals(newest,current().snapshotVersion()); assertEquals(visits,current().currentPeople().longValue());
        assertNull(jdbc.queryForObject("SELECT invalid_reason FROM space_snapshot WHERE id=?",String.class,newest));
        assertThrows(com.campusflow.common.BusinessException.class, () -> inspection.validity(InspectionService.Kind.PEOPLE,newest,false,"  ","data_admin"));
    }
    @Test void invalidNoiseIsExcludedFromMedianAndOldRestorationDoesNotRefreshTime() {
        simulation.control(false,"test");
        jdbc.update("UPDATE noise_sample SET valid=FALSE WHERE run_id=? AND space_id=6",simulation.runId());
        assertEquals("INVALID",current().noiseState()); assertNull(current().typicalNoiseDb());
        long id = latest("noise_sample");
        inspection.validity(InspectionService.Kind.NOISE,id,true,"确认读数","data_admin");
        jdbc.update("UPDATE noise_sample SET noise_db=40 WHERE id=?",id);
        clock.advance(5); simulation.control(true,"test"); simulation.tick();
        long newest = latest("noise_sample"); jdbc.update("UPDATE noise_sample SET noise_db=80 WHERE id=?",newest);
        assertEquals(60.0,current().typicalNoiseDb());
        inspection.validity(InspectionService.Kind.NOISE,newest,false,"异常尖峰","data_admin");
        assertEquals(40.0,current().typicalNoiseDb());
        clock.advance(31);
        inspection.validity(InspectionService.Kind.NOISE,newest,true,"恢复读数","data_admin");
        assertEquals("EXPIRED",current().noiseState()); assertNull(current().noiseDb());
    }
    @Test void offlineIsImmediateAndPauseExpiresEachMetricIndependently() {
        simulation.device(6,false,"server_admin"); assertEquals("OFFLINE",current().noiseState());
        assertNull(current().quietLevel()); assertEquals("VALID",current().peopleState());
        long samples = count("noise_sample"); simulation.tick(); assertEquals(samples + 9,count("noise_sample"));
        clock.advance(15); simulation.tick(); simulation.control(false,"server_admin");
        clock.advance(16); simulation.device(6,true,"server_admin");
        assertEquals("EXPIRED",current().noiseState()); assertEquals("VALID",current().peopleState());
        long snapshots = count("space_snapshot"); simulation.tick(); assertEquals(snapshots,count("space_snapshot"));
        clock.advance(15); assertEquals("EXPIRED",current().peopleState());
    }
    @Test void peakIsBoundedNoiseEventEndsAndClosedSpaceClearsWhilePaused() {
        simulation.changeScenario(SimulationService.Scenario.PEAK,null,55,60,"server_admin");
        long initial = simulation.currentPeople(6); simulation.tick(); assertTrue(simulation.currentPeople(6) > initial);
        for (int i=0; i<12; i++) { clock.advance(5); simulation.tick(); }
        assertEquals(spaces.selectById(6).capacity.longValue(),simulation.currentPeople(6));
        simulation.changeScenario(SimulationService.Scenario.NOISE_EVENT,6L,127,10,"server_admin");
        simulation.tick(); assertTrue(current().noiseDb() >= 70);
        clock.advance(10); simulation.tick(); assertTrue(current().noiseDb() <= 59);
        simulation.control(false,"server_admin");
        jdbc.update("UPDATE study_space SET all_day=FALSE,open_time='08:00',close_time='11:00' WHERE id=6");
        clock.advance(3600); simulation.tick(); assertEquals(0,simulation.currentPeople(6));
        assertEquals(0,current().currentPeople());
    }
    @Test void resetPreservesBusinessAndLogsAndReplaysSeededState() {
        var configured=configs.update(new com.campusflow.system.ConfigInput(configs.current().version(),3,60,90,.3,.3,.25,.15),"test");
        jdbc.update("INSERT INTO space_review(user_id,space_id,environment_score,facility_score,content,status,updated_at) SELECT id,6,4,5,'测试保留','PENDING',? FROM sys_user WHERE username='student' AND NOT EXISTS(SELECT 1 FROM space_review WHERE space_id=6)",LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));
        long accounts = count("sys_user"), reviews = count("space_review"), logs = count("audit_log"), oldRun = simulation.runId();
        simulation.changeScenario(SimulationService.Scenario.NORMAL,null,987,60,"test");
        simulation.tick();
        var first = jdbc.queryForList("SELECT space_id,current_people FROM space_snapshot WHERE run_id=? ORDER BY id",oldRun);
        var firstNoise = jdbc.queryForList("SELECT space_id,noise_db FROM noise_sample WHERE run_id=? ORDER BY id",oldRun);
        simulation.control(false,"test"); simulation.reset("server_admin");
        assertFalse(simulation.running()); assertNotEquals(oldRun,simulation.runId());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sim_visit WHERE run_id=?",Integer.class,oldRun));
        assertEquals(accounts,count("sys_user")); assertEquals(reviews,count("space_review")); assertEquals(10,count("study_space")); assertTrue(count("audit_log") > logs);
        assertEquals(configured,configs.current()); assertEquals(60,current().validitySeconds());
        assertEquals(SimulationService.Scenario.NORMAL,simulation.info().scenario()); assertEquals(127,simulation.info().seed());
        simulation.control(true,"test"); simulation.changeScenario(SimulationService.Scenario.NORMAL,null,987,60,"test"); simulation.tick();
        assertEquals(first,jdbc.queryForList("SELECT space_id,current_people FROM space_snapshot WHERE run_id=? ORDER BY id",simulation.runId()));
        assertEquals(firstNoise,jdbc.queryForList("SELECT space_id,noise_db FROM noise_sample WHERE run_id=? ORDER BY id",simulation.runId()));
    }
    @Test void newEndpointsEnforceRolesCsrfValidationAndPagination() throws Exception {
        var student=login("student"); var data=login("data_admin"); var server=login("server_admin");
        mvc.perform(get("/api/data/records").param("kind","PEOPLE")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/data/devices").session(server)).andExpect(status().isForbidden());
        mvc.perform(put("/api/system/devices/6").session(data).with(csrf()).contentType("application/json").content("{\"online\":false}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/system/simulation/reset").session(server)).andExpect(status().isForbidden());
        mvc.perform(put("/api/system/simulation/scenario").session(server).with(csrf()).contentType("application/json").content("{\"scenario\":\"NOISE_EVENT\",\"durationSeconds\":0}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/system/simulation/scenario").session(server).with(csrf()).contentType("application/json").content("{\"scenario\":\"NOISE_EVENT\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/data/records").session(data).param("kind","PEOPLE").param("spaceId","6").param("pageSize","1")).andExpect(status().isOk()).andExpect(jsonPath("total").value(1)).andExpect(jsonPath("items[0].spaceId").value(6)).andExpect(jsonPath("items[0].sampledAt").value("2026-10-09T02:00:00Z"));
        mvc.perform(get("/api/data/records").session(data).param("kind","PEOPLE").param("pageSize","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/data/records").session(data).param("kind","oops")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/data/records/PEOPLE/" + latest("space_snapshot") + "/validity").session(data).with(csrf()).contentType("application/json").content("{\"valid\":false,\"reason\":\"异常\"}")).andExpect(status().isNoContent());
        assertEquals("INVALID",current().peopleState()); assertNull(current().currentPeople());
        mvc.perform(get("/api/data/records").session(data).param("kind","PEOPLE").param("spaceId","6").param("valid","false")).andExpect(status().isOk()).andExpect(jsonPath("total").value(1));
        mvc.perform(get("/api/data/records").session(data).param("kind","VISIT")).andExpect(status().isOk()).andExpect(jsonPath("items[0].virtualPersonId").isNotEmpty()).andExpect(jsonPath("items[0].checkedInAt").value("2026-10-09T02:00:00Z"));
    }
    @Test void concurrentTicksAndResetDoNotMixRunsOrExceedCapacity() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var ticks = executor.submit(() -> { for (int i=0;i<20;i++) simulation.tick(); });
            var resets = executor.submit(() -> { for (int i=0;i<5;i++) simulation.reset("test"); });
            ticks.get(20,TimeUnit.SECONDS); resets.get(20,TimeUnit.SECONDS);
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM space_snapshot p JOIN study_space s ON s.id=p.space_id WHERE p.current_people>s.capacity OR p.current_people<0",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sim_visit WHERE run_id<>? AND checked_out_at IS NULL",Integer.class,simulation.runId()));
            for (var space : spaces.selectList(null)) assertEquals(simulation.currentPeople(space.id),statuses.current(space).currentPeople().longValue());
        } finally { executor.shutdownNow(); }
    }
    @Test void resetMakesStaleRecordEditsFailAndInvalidScenarioRollsBack() {
        long id = latest("space_snapshot"); var before = simulation.info();
        assertThrows(com.campusflow.common.BusinessException.class, () -> simulation.changeScenario(SimulationService.Scenario.NOISE_EVENT,999L,127,60,"test"));
        assertEquals(before,simulation.info());
        simulation.reset("test");
        assertThrows(com.campusflow.common.BusinessException.class, () -> inspection.validity(InspectionService.Kind.PEOPLE,id,false,"旧记录","test"));
        assertThrows(com.campusflow.common.BusinessException.class, () -> inspection.validity(InspectionService.Kind.VISIT,1,false,"无效","test"));
    }
    @Test void scenariosAndInvalidRecordsChangeRecommendationAndHardFilters() throws Exception {
        var student=login("student"); var server=login("server_admin");
        var before = json.readTree(mvc.perform(get("/api/spaces/6").session(student)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        simulation.changeScenario(SimulationService.Scenario.NOISE_EVENT,6L,127,120,"test");
        for (int i=0;i<13;i++) { clock.advance(5); simulation.tick(); }
        var noisy = json.readTree(mvc.perform(get("/api/spaces/6").session(student)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(noisy.get("status").get("typicalNoiseDb").asDouble() >= 70);
        assertTrue(noisy.get("score").asDouble() < before.get("score").asDouble());
        mvc.perform(put("/api/system/devices/6").session(server).with(csrf()).contentType("application/json").content("{\"online\":false}")).andExpect(status().isOk());
        var quietFiltered = json.readTree(mvc.perform(get("/api/spaces").session(student).param("openOnly","false").param("minQuiet","1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (var item : quietFiltered.get("items")) assertNotEquals(6,item.get("space").get("id").asLong());
        jdbc.update("UPDATE space_snapshot SET valid=FALSE WHERE run_id=? AND space_id=6",simulation.runId());
        var occupancyFiltered = json.readTree(mvc.perform(get("/api/spaces").session(student).param("openOnly","false").param("maxOccupancy","1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (var item : occupancyFiltered.get("items")) assertNotEquals(6,item.get("space").get("id").asLong());
        mvc.perform(post("/api/system/simulation/reset").session(server).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("scenario").value("NORMAL")).andExpect(jsonPath("seed").value(127));
        mvc.perform(get("/api/system/devices").session(server)).andExpect(status().isOk()).andExpect(jsonPath("[5].status").value("ONLINE"));
    }
    @Test void fallbackCannotReturnSnapshotAboveReducedCapacity() {
        assertTrue(current().currentPeople() > 1);
        jdbc.update("UPDATE sim_visit SET checked_out_at=? WHERE space_id=6 AND checked_out_at IS NULL",LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));
        jdbc.update("UPDATE study_space SET capacity=1 WHERE id=6");
        clock.advance(5); simulation.tick();
        inspection.validity(InspectionService.Kind.PEOPLE,latest("space_snapshot"),false,"最新采样异常","test");
        assertNull(current().currentPeople()); assertEquals("INVALID",current().peopleState());
        jdbc.update("UPDATE study_space SET capacity=16 WHERE id=6");
    }
}

