package com.orchestragent.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.orchestragent.testsupport.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = "orchestragent.dev-login-code=424242")
class DevDataSeederTest extends IntegrationTest {

    @Autowired
    DevDataSeeder seeder;

    @Test
    void theDemoUserCanSignInAndFindsAWorkspaceWithIssuesInEveryKindOfStatus() throws Exception {
        Cookie session = verifyCode(DevDataSeeder.DEMO_EMAIL, "424242").andExpect(status().isOk())
                .andReturn().getResponse().getCookie("oa_session");

        String workspaces = mvc.perform(get("/api/workspaces").cookie(session)).andReturn().getResponse().getContentAsString();
        List<String> names = JsonPath.read(workspaces, "$[*].name");
        assertThat(names).containsExactly(DevDataSeeder.WORKSPACE_NAME);
        String workspaceId = JsonPath.read(workspaces, "$[0].id");

        String issues = mvc.perform(get("/api/workspaces/" + workspaceId + "/issues").cookie(session))
                .andReturn().getResponse().getContentAsString();
        List<String> statuses = JsonPath.read(issues, "$.items[*].status");
        assertThat(statuses).hasSize(8).contains("backlog", "todo", "in_progress", "in_review", "done", "blocked", "cancelled");
    }

    @Test
    void runningTheSeederAgainAddsNothing() throws Exception {
        Cookie session = verifyCode(DevDataSeeder.DEMO_EMAIL, "424242").andReturn().getResponse().getCookie("oa_session");

        seeder.seed();
        seeder.seed();

        String workspaces = mvc.perform(get("/api/workspaces").cookie(session)).andReturn().getResponse().getContentAsString();
        assertThat((List<?>) JsonPath.read(workspaces, "$")).hasSize(1);
    }
}
