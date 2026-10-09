package com.campusflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired com.campusflow.space.SpaceMapper spaceMapper;
    @BeforeEach void secondUser() {
        jdbc.update("INSERT INTO sys_user(username,password_hash,role,enabled,created_at) VALUES(?,?,'USER',TRUE,CURRENT_TIMESTAMP)","review_test_other",encoder.encode("Demo@123456"));
    }
    MockHttpSession login(String name) throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).param("username",name).param("password","Demo@123456"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    JsonNode submit(MockHttpSession user, int environment, int facility, long version) throws Exception {
        return json.readTree(mvc.perform(put("/api/user/reviews/6").session(user).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("environmentScore",environment,"facilityScore",facility,"content","学习环境不错","expectedVersion",version))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    JsonNode decision(MockHttpSession admin, JsonNode review, String action, String reason) throws Exception {
        return json.readTree(mvc.perform(post("/api/data/reviews/"+review.get("id").asLong()+"/decision").session(admin).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("action",action,"reason",reason,"expectedVersion",review.get("version").asLong()))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void submitApproveModifyRejectResubmitRevokeAndWithdrawUpdateSummary() throws Exception {
        var user=login("student"); var admin=login("data_admin");
        var review=submit(user,5,3,0);
        Assertions.assertEquals("PENDING",review.get("status").asText());
        mvc.perform(get("/api/spaces/6/reviews").session(user)).andExpect(jsonPath("summary.count").value(0)).andExpect(jsonPath("summary.environmentAverage").value(nullValue()));
        review=decision(admin,review,"APPROVE","");
        mvc.perform(get("/api/spaces/6/reviews").session(user)).andExpect(jsonPath("summary.count").value(1))
            .andExpect(jsonPath("summary.environmentAverage").value(5.0)).andExpect(jsonPath("summary.facilityAverage").value(3.0))
            .andExpect(jsonPath("items[0].reviewReason").doesNotExist()).andExpect(jsonPath("items[0].reviewedBy").doesNotExist());
        mvc.perform(get("/api/spaces/6").session(user)).andExpect(jsonPath("reviewSummary.count").value(1));
        review=submit(user,4,4,review.get("version").asLong());
        mvc.perform(get("/api/spaces/6/reviews").session(user)).andExpect(jsonPath("summary.count").value(0));
        review=decision(admin,review,"REJECT","说明不够具体");
        mvc.perform(get("/api/user/reviews/6").session(user)).andExpect(jsonPath("review.reviewReason").value("说明不够具体"));
        review=submit(user,4,4,review.get("version").asLong());
        review=decision(admin,review,"APPROVE","");
        review=decision(admin,review,"REVOKE","重复无意义内容");
        Assertions.assertEquals("REJECTED",review.get("status").asText());
        review=submit(user,5,5,review.get("version").asLong());
        review=decision(admin,review,"APPROVE","");
        mvc.perform(post("/api/user/reviews/6/withdraw").session(user).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("expectedVersion",review.get("version").asLong()))))
            .andExpect(status().isOk()).andExpect(jsonPath("status").value("WITHDRAWN"));
        mvc.perform(get("/api/spaces/6/reviews").session(user)).andExpect(jsonPath("summary.count").value(0));
        Assertions.assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action LIKE 'REVIEW_%'",Long.class)>=10);
    }
    @Test void averagesContainOnlyApprovedCurrentReviewsAndFilterAdminQueue() throws Exception {
        var user=login("student"); var other=login("review_test_other"); var admin=login("data_admin");
        decision(admin,submit(user,5,3,0),"APPROVE","");
        decision(admin,submit(other,3,5,0),"APPROVE","");
        mvc.perform(get("/api/spaces/6/reviews").session(user)).andExpect(jsonPath("summary.count").value(2))
            .andExpect(jsonPath("summary.environmentAverage").value(4.0)).andExpect(jsonPath("summary.facilityAverage").value(4.0));
        mvc.perform(get("/api/data/reviews").session(admin).param("spaceId","6").param("username","review_test_other").param("status","APPROVED"))
            .andExpect(status().isOk()).andExpect(jsonPath("total").value(1));
        mvc.perform(get("/api/user/reviews").session(user)).andExpect(jsonPath("total").value(1)).andExpect(jsonPath("items[0].username").value("student"));
    }
    @Test void staleSubmissionsAndStaleModerationCannotOverwriteCurrentContent() throws Exception {
        var user=login("student"); var admin=login("data_admin");
        var first=submit(user,5,3,0);
        var changed=submit(user,4,4,first.get("version").asLong());
        mvc.perform(post("/api/data/reviews/"+first.get("id").asLong()+"/decision").session(admin).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("action","APPROVE","reason","","expectedVersion",first.get("version").asLong()))))
            .andExpect(status().isConflict()).andExpect(jsonPath("code").value("REVIEW_CHANGED"));
        mvc.perform(put("/api/user/reviews/6").session(user).with(csrf()).contentType("application/json")
            .content("{\"environmentScore\":5,\"facilityScore\":5,\"content\":\"重复提交\",\"expectedVersion\":0}"))
            .andExpect(status().isConflict());
        Assertions.assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM space_review WHERE space_id=6",Long.class));
        decision(admin,changed,"APPROVE","");
    }
    @Test void validationOwnershipRoleAndDisabledSpaceAreEnforced() throws Exception {
        var user=login("student"); var other=login("review_test_other"); var admin=login("data_admin"); var server=login("server_admin");
        mvc.perform(get("/api/user/reviews").session(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/data/reviews").session(user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/data/reviews").session(server)).andExpect(status().isForbidden());
        mvc.perform(put("/api/user/reviews/6").session(user).with(csrf()).contentType("application/json")
            .content("{\"environmentScore\":6,\"facilityScore\":1,\"content\":\"\",\"expectedVersion\":0}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/user/reviews/6").session(user).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("environmentScore",5,"facilityScore",5,"content","字".repeat(501),"expectedVersion",0))))
            .andExpect(status().isBadRequest());
        var review=submit(user,5,5,0);
        mvc.perform(post("/api/user/reviews/6/withdraw").session(other).with(csrf()).contentType("application/json").content("{\"expectedVersion\":1}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/data/reviews/"+review.get("id").asLong()+"/decision").session(admin).with(csrf()).contentType("application/json")
            .content("{\"action\":\"REJECT\",\"reason\":\"  \",\"expectedVersion\":1}"))
            .andExpect(status().isBadRequest());
        var disabledSpace=spaceMapper.selectById(6L);disabledSpace.enabled=false;spaceMapper.updateById(disabledSpace);
        mvc.perform(get("/api/user/reviews/6").session(user)).andExpect(status().isOk()).andExpect(jsonPath("spaceEnabled").value(false));
        mvc.perform(put("/api/user/reviews/6").session(user).with(csrf()).contentType("application/json")
            .content("{\"environmentScore\":4,\"facilityScore\":4,\"content\":\"\",\"expectedVersion\":1}"))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/user/reviews/6/withdraw").session(user).with(csrf()).contentType("application/json").content("{\"expectedVersion\":1}"))
            .andExpect(status().isOk());
    }
    @Test void fractionalRatingsMustNotBeSilentlyTruncated() throws Exception {
        var user=login("student");
        mvc.perform(put("/api/user/reviews/6").session(user).with(csrf()).contentType("application/json")
            .content("{\"environmentScore\":2.5,\"facilityScore\":4,\"content\":\"\",\"expectedVersion\":0}"))
            .andExpect(status().isBadRequest());
    }
}
