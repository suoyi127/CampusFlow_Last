package com.campusflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:real_only;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "spring.datasource.username=sa", "spring.datasource.password=", "campusflow.checkin.mqtt.enabled=false", "spring.flyway.locations=classpath:db/real-migration"})
@AutoConfigureMockMvc
class RealDatabaseIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Test void freshRealDatabaseContainsClassroomCatalogWithoutSampleAccountsOrHardware() {
        for(String table:new String[]{"sys_user","hardware_device","hardware_record"})
            assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class));
        assertEquals(48L, jdbc.queryForObject("SELECT COUNT(*) FROM study_space", Long.class));
        assertEquals(7620L, jdbc.queryForObject("SELECT SUM(capacity) FROM study_space", Long.class));
        for (int floor = 1; floor <= 6; floor++) {
            for (int room = 1; room <= 8; room++) {
                int expected = room == 1 ? 70 : room == 4 ? 300 : 150;
                String name = "湖南科技大学第一教学楼" + (floor * 100 + room) + "教室";
                assertEquals(expected, jdbc.queryForObject("""
                    SELECT capacity FROM study_space
                    WHERE name = ? AND type = 'CLASSROOM' AND data_origin = 'REAL'
                      AND enabled = FALSE AND coordinate_system = 'GCJ02' AND latitude = 27.895403 AND longitude = 112.923703
                    """, Integer.class, name));
            }
        }
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN ('simulation_run','sim_visit','space_snapshot','noise_sample','noise_device')",Long.class));
    }
    @Test void anonymousSessionCanGetCsrfButCannotReadBusinessData() throws Exception {
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("token").isNotEmpty());
        mvc.perform(get("/api/spaces").param("latitude","31.2304").param("longitude","121.4737")).andExpect(status().isUnauthorized());
    }
}
