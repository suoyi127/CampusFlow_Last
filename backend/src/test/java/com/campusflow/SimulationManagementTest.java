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

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class SimulationManagementTest {
    @Autowired MockMvc mvc;
    MockHttpSession admin() throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf())
            .param("username","server_admin").param("password","Demo@123456"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    @Test void overviewOnlyReportsRealServicesAndHardware() throws Exception {
        mvc.perform(get("/api/system/overview").session(admin())).andExpect(status().isOk())
            .andExpect(jsonPath("dataSource").value("HARDWARE"))
            .andExpect(jsonPath("deviceCount").value(0))
            .andExpect(jsonPath("simulationRunId").doesNotExist())
            .andExpect(jsonPath("scenario").doesNotExist());
    }
    @Test void generationControlsAreRemoved() throws Exception {
        var session=admin();
        for (String action:new String[]{"resume","pause","reset"})
            mvc.perform(post("/api/system/simulation/"+action).session(session).with(csrf()))
                .andExpect(status().isNotFound());
    }
}
