package com.orchestragent.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.orchestragent.testsupport.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class ProjectApiTest extends IntegrationTest {

    private Cookie owner;
    private Cookie admin;
    private Cookie member;
    private Cookie colleague;
    private Cookie outsider;
    private String memberId;
    private String workspaceId;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = uniqueEmail();
        String memberEmail = uniqueEmail();
        String colleagueEmail = uniqueEmail();
        owner = login(uniqueEmail());
        admin = login(adminEmail);
        member = login(memberEmail);
        colleague = login(colleagueEmail);
        outsider = login(uniqueEmail());
        memberId = JsonPath.read(
                mvc.perform(get("/api/me").cookie(member)).andReturn().getResponse().getContentAsString(), "$.id");

        workspaceId = createWorkspace(owner);
        join(admin, adminEmail, "admin");
        join(member, memberEmail, "member");
        join(colleague, colleagueEmail, "member");
        base = "/api/workspaces/" + workspaceId + "/projects";
    }

    private String createWorkspace(Cookie by) throws Exception {
        return JsonPath.read(
                mvc.perform(json(post("/api/workspaces"), "{\"name\":\"Projects " + UUID.randomUUID().toString().substring(0, 8) + "\"}")
                                .cookie(by).with(xsrf()))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.id");
    }

    private void join(Cookie person, String email, String role) throws Exception {
        String invitation = mvc.perform(json(post("/api/workspaces/" + workspaceId + "/invitations"),
                        "{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}").cookie(owner).with(xsrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/invitations/" + JsonPath.read(invitation, "$.id") + "/accept").cookie(person).with(xsrf()))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, Cookie cookie) throws Exception {
        return builder.cookie(cookie).with(xsrf());
    }

    private List<String> keys(String query, Cookie by) throws Exception {
        return JsonPath.read(
                mvc.perform(get(base + query).cookie(by)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$[*].key");
    }

    // ---- creating and reading ----------------------------------------------------------------------------

    @Test
    void anyMemberCreatesAProjectWithANormalizedKey() throws Exception {
        mvc.perform(as(json(post(base), "{\"key\":\" web \",\"name\":\"  Web app \",\"description\":\"Pages\"}"), member))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("WEB"))
                .andExpect(jsonPath("$.name").value("Web app"))
                .andExpect(jsonPath("$.description").value("Pages"))
                .andExpect(jsonPath("$.createdById").value(memberId))
                .andExpect(jsonPath("$.createdByName").isNotEmpty())
                .andExpect(jsonPath("$.archived").value(false));

        mvc.perform(get(base + "/key/web").cookie(colleague))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("WEB"));
    }

    @Test
    void keysAreUniqueInAWorkspaceIgnoringCaseButFreeInAnother() throws Exception {
        createProject(workspaceId, member, "WEB");

        mvc.perform(as(json(post(base), "{\"key\":\"web\",\"name\":\"Again\"}"), admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("project.key_taken"));

        String elsewhere = createWorkspace(member);
        createProject(elsewhere, member, "WEB");
        assertThat(keys("", member)).containsExactly("WEB");
    }

    @Test
    void validatesTheInput() throws Exception {
        for (String body : List.of(
                "{}",
                "{\"name\":\"No key\"}",
                "{\"key\":\"WEB\"}",
                "{\"key\":\"WEB\",\"name\":\"  \"}",
                "{\"key\":\"1WEB\",\"name\":\"Digit first\"}",
                "{\"key\":\"W\",\"name\":\"Too short\"}",
                "{\"key\":\"ABCDEFGHIJK\",\"name\":\"Too long\"}",
                "{\"key\":\"WE-B\",\"name\":\"Dash\"}",
                "{\"key\":\"WEB\",\"name\":\"" + "x".repeat(81) + "\"}")) {
            mvc.perform(as(json(post(base), body), member)).andExpect(status().isBadRequest());
        }
        mvc.perform(as(json(post(base), "{\"key\":\"1WEB\",\"name\":\"Digit first\"}"), member))
                .andExpect(jsonPath("$.code").value("project.invalid_key"));
        assertThat(keys("?includeArchived=true", member)).isEmpty();
    }

    @Test
    void listsByKeyWithArchivedOnlyWhenAsked() throws Exception {
        createProject(workspaceId, member, "WEB");
        String api = createProject(workspaceId, member, "API");
        createProject(workspaceId, member, "DOC");
        mvc.perform(as(post(base + "/" + api + "/archive"), member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());

        assertThat(keys("", colleague)).containsExactly("DOC", "WEB");
        assertThat(keys("?includeArchived=true", colleague)).containsExactly("API", "DOC", "WEB");
    }

    // ---- changing ----------------------------------------------------------------------------------------

    @Test
    void theCreatorAdminsAndOwnersEditButOtherMembersCannot() throws Exception {
        String web = createProject(workspaceId, member, "WEB");

        mvc.perform(as(json(patch(base + "/" + web), "{\"name\":\"Website\"}"), member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Website"))
                .andExpect(jsonPath("$.key").value("WEB"));
        mvc.perform(as(json(patch(base + "/" + web), "{\"description\":\"By the admin\"}"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Website"))
                .andExpect(jsonPath("$.description").value("By the admin"));
        mvc.perform(as(json(patch(base + "/" + web), "{\"name\":\"By the owner\"}"), owner)).andExpect(status().isOk());

        mvc.perform(as(json(patch(base + "/" + web), "{\"name\":\"Nope\"}"), colleague))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("project.manage_forbidden"));
        mvc.perform(as(post(base + "/" + web + "/archive"), colleague)).andExpect(status().isForbidden());
        mvc.perform(as(post(base + "/" + web + "/restore"), colleague)).andExpect(status().isForbidden());
    }

    @Test
    void theKeyCannotBeChanged() throws Exception {
        String web = createProject(workspaceId, member, "WEB");

        mvc.perform(as(json(patch(base + "/" + web), "{\"key\":\"NEW\",\"name\":\"Renamed\"}"), member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("WEB"));
    }

    @Test
    void anArchivedProjectCannotBeEditedUntilRestored() throws Exception {
        String web = createProject(workspaceId, member, "WEB");
        mvc.perform(as(post(base + "/" + web + "/archive"), admin)).andExpect(status().isOk());

        mvc.perform(as(json(patch(base + "/" + web), "{\"name\":\"Renamed\"}"), member))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("project.archived"));

        mvc.perform(as(post(base + "/" + web + "/restore"), member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));
        mvc.perform(as(json(patch(base + "/" + web), "{\"name\":\"Renamed\"}"), member)).andExpect(status().isOk());
    }

    // ---- isolation ---------------------------------------------------------------------------------------

    @Test
    void anOutsiderGetsNotFoundEverywhere() throws Exception {
        String web = createProject(workspaceId, member, "WEB");

        mvc.perform(get(base).cookie(outsider)).andExpect(status().isNotFound());
        mvc.perform(get(base + "/" + web).cookie(outsider)).andExpect(status().isNotFound());
        mvc.perform(get(base + "/key/WEB").cookie(outsider)).andExpect(status().isNotFound());
        mvc.perform(as(json(post(base), "{\"key\":\"OUT\",\"name\":\"Out\"}"), outsider)).andExpect(status().isNotFound());
        mvc.perform(as(json(patch(base + "/" + web), "{\"name\":\"x\"}"), outsider)).andExpect(status().isNotFound());
        mvc.perform(as(post(base + "/" + web + "/archive"), outsider)).andExpect(status().isNotFound());
    }

    @Test
    void aProjectIsNotReachableThroughAnotherWorkspaceOfTheSameUser() throws Exception {
        String web = createProject(workspaceId, member, "WEB");
        String elsewhere = createWorkspace(member);
        String otherBase = "/api/workspaces/" + elsewhere + "/projects";

        mvc.perform(get(otherBase + "/" + web).cookie(member)).andExpect(status().isNotFound());
        mvc.perform(get(otherBase + "/key/WEB").cookie(member)).andExpect(status().isNotFound());
        mvc.perform(as(json(patch(otherBase + "/" + web), "{\"name\":\"x\"}"), member)).andExpect(status().isNotFound());
        mvc.perform(as(post(otherBase + "/" + web + "/archive"), member)).andExpect(status().isNotFound());
    }

    @Test
    void writesNeedASessionAndCsrf() throws Exception {
        mvc.perform(json(post(base), "{\"key\":\"WEB\",\"name\":\"x\"}").with(xsrf())).andExpect(status().isUnauthorized());
        mvc.perform(json(post(base), "{\"key\":\"WEB\",\"name\":\"x\"}").cookie(member)).andExpect(status().isForbidden());
    }
}
