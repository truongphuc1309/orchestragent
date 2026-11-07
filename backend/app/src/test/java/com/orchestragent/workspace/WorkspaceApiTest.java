package com.orchestragent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.orchestragent.testsupport.IntegrationTest;
import com.orchestragent.workspace.application.port.InvitationNotifier;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The workspace REST API end to end, with the focus on who may do what. */
class WorkspaceApiTest extends IntegrationTest {

    private Cookie ownerCookie;
    private Cookie adminCookie;
    private Cookie memberCookie;
    private Cookie outsiderCookie;
    private String ownerEmail;
    private String adminEmail;
    private String memberEmail;
    private String ownerId;
    private String adminId;
    private String memberId;
    private String workspaceId;

    @BeforeEach
    void setUp() throws Exception {
        ownerEmail = uniqueEmail();
        adminEmail = uniqueEmail();
        memberEmail = uniqueEmail();
        ownerCookie = login(ownerEmail);
        adminCookie = login(adminEmail);
        memberCookie = login(memberEmail);
        outsiderCookie = login(uniqueEmail());
        ownerId = userId(ownerCookie);
        adminId = userId(adminCookie);
        memberId = userId(memberCookie);

        workspaceId = JsonPath.read(createWorkspace(ownerCookie, "Acme " + UUID.randomUUID().toString().substring(0, 8)), "$.id");
        join(adminCookie, adminEmail, "admin");
        join(memberCookie, memberEmail, "member");
    }

    // ---- helpers ----------------------------------------------------------------------------------------

    private String userId(Cookie cookie) throws Exception {
        String body = mvc.perform(get("/api/me").cookie(cookie)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String createWorkspace(Cookie cookie, String name) throws Exception {
        return mvc.perform(json(post("/api/workspaces"), "{\"name\":\"" + name + "\"}").cookie(cookie).with(xsrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String invite(Cookie by, String email, String role) throws Exception {
        return mvc.perform(json(post("/api/workspaces/" + workspaceId + "/invitations"),
                        "{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}").cookie(by).with(xsrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    /** Owner invites the address, then the person signs in with it and accepts. */
    private void join(Cookie person, String email, String role) throws Exception {
        String invitationId = JsonPath.read(invite(ownerCookie, email, role), "$.id");
        mvc.perform(post("/api/invitations/" + invitationId + "/accept").cookie(person).with(xsrf()))
                .andExpect(status().isOk());
    }

    private String members(Cookie cookie) throws Exception {
        return mvc.perform(get("/api/workspaces/" + workspaceId + "/members").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String roleOf(String userId) throws Exception {
        // A filter expression always yields a list, even when exactly one member matches.
        List<String> roles = JsonPath.read(members(ownerCookie), "$[?(@.userId=='" + userId + "')].role");
        assertThat(roles).as("member %s in the list", userId).hasSize(1);
        return roles.getFirst();
    }

    private static String role(String role) {
        return "{\"role\":\"" + role + "\"}";
    }

    // ---- creating and listing workspaces -----------------------------------------------------------------

    @Test
    void creatorBecomesOwnerAndSeesTheWorkspaceInTheirList() throws Exception {
        String body = createWorkspace(outsiderCookie, "Zeta Labs");

        assertThat((String) JsonPath.read(body, "$.role")).isEqualTo("owner");
        assertThat((String) JsonPath.read(body, "$.name")).isEqualTo("Zeta Labs");
        assertThat((String) JsonPath.read(body, "$.slug")).startsWith("zeta-labs");
        mvc.perform(get("/api/workspaces").cookie(outsiderCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Zeta Labs')].role").value("owner"));
    }

    @Test
    void workspacesWithTheSameNameGetDistinctSlugs() throws Exception {
        String name = "Same " + UUID.randomUUID().toString().substring(0, 8);

        String first = JsonPath.read(createWorkspace(ownerCookie, name), "$.slug");
        String second = JsonPath.read(createWorkspace(memberCookie, name), "$.slug");

        assertThat(second).isEqualTo(first + "-2");
    }

    @Test
    void theListOnlyContainsWorkspacesOfTheCaller() throws Exception {
        mvc.perform(get("/api/workspaces").cookie(outsiderCookie)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/workspaces").cookie(memberCookie))
                .andExpect(jsonPath("$[?(@.id=='" + workspaceId + "')].role").value("member"));
    }

    @Test
    void invalidWorkspaceNamesAreRejected() throws Exception {
        for (String name : new String[] {"   ", "x".repeat(61), "Bad\\nName"}) {
            mvc.perform(json(post("/api/workspaces"), "{\"name\":\"" + name + "\"}").cookie(ownerCookie).with(xsrf()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void everythingNeedsLogin() throws Exception {
        mvc.perform(get("/api/workspaces")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/workspaces/" + workspaceId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/workspaces/" + workspaceId + "/members")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/invitations")).andExpect(status().isUnauthorized());
    }

    @Test
    void apiTokensDoNotGrantAccessToTheBrowserApi() throws Exception {
        String secret = JsonPath.read(
                mvc.perform(json(post("/api/me/tokens"), "{\"name\":\"cli\"}").cookie(ownerCookie).with(xsrf()))
                        .andReturn().getResponse().getContentAsString(),
                "$.secret");

        mvc.perform(get("/api/workspaces").header("Authorization", "Bearer " + secret)).andExpect(status().isUnauthorized());
    }

    // ---- who can see a workspace -------------------------------------------------------------------------

    @Test
    void membersCanReadTheWorkspaceAndItsMemberList() throws Exception {
        mvc.perform(get("/api/workspaces/" + workspaceId).cookie(memberCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("member"));

        mvc.perform(get("/api/workspaces/" + workspaceId + "/members").cookie(memberCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.userId=='" + ownerId + "')].role").value("owner"))
                .andExpect(jsonPath("$[?(@.userId=='" + adminId + "')].email").value(adminEmail))
                .andExpect(jsonPath("$[?(@.userId=='" + memberId + "')].role").value("member"));
    }

    @Test
    void outsidersGetNotFoundEverywhereAndCannotTellTheWorkspaceExists() throws Exception {
        String unknown = UUID.randomUUID().toString();

        String forExisting = mvc.perform(get("/api/workspaces/" + workspaceId).cookie(outsiderCookie))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String forMissing = mvc.perform(get("/api/workspaces/" + unknown).cookie(outsiderCookie))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(forExisting, "$.code")).isEqualTo(JsonPath.read(forMissing, "$.code"));
        assertThat((String) JsonPath.read(forExisting, "$.detail")).isEqualTo(JsonPath.read(forMissing, "$.detail"));

        mvc.perform(get("/api/workspaces/" + workspaceId + "/members").cookie(outsiderCookie)).andExpect(status().isNotFound());
        mvc.perform(get("/api/workspaces/" + workspaceId + "/invitations").cookie(outsiderCookie)).andExpect(status().isNotFound());
        mvc.perform(json(post("/api/workspaces/" + workspaceId + "/invitations"),
                        "{\"email\":\"x@example.com\",\"role\":\"member\"}").cookie(outsiderCookie).with(xsrf()))
                .andExpect(status().isNotFound());
        mvc.perform(json(patch("/api/workspaces/" + workspaceId + "/members/" + memberId), role("admin"))
                        .cookie(outsiderCookie).with(xsrf()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/workspaces/" + workspaceId + "/members/" + memberId).cookie(outsiderCookie).with(xsrf()))
                .andExpect(status().isNotFound());
        assertThat(roleOf(memberId)).isEqualTo("member");
    }

    @Test
    void aMalformedWorkspaceIdIsABadRequest() throws Exception {
        mvc.perform(get("/api/workspaces/not-a-uuid").cookie(ownerCookie)).andExpect(status().isBadRequest());
    }

    // ---- inviting ----------------------------------------------------------------------------------------

    @Test
    void aPlainMemberCannotInviteAnyoneAndNoEmailIsSent() throws Exception {
        mvc.perform(json(post("/api/workspaces/" + workspaceId + "/invitations"),
                        "{\"email\":\"friend@example.com\",\"role\":\"member\"}").cookie(memberCookie).with(xsrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("invitation.forbidden"));

        verify(invitationNotifier, never()).invited(org.mockito.ArgumentMatchers.argThat(n -> "friend@example.com".equals(n.recipientEmail())));
        mvc.perform(get("/api/workspaces/" + workspaceId + "/invitations").cookie(memberCookie)).andExpect(status().isForbidden());
    }

    @Test
    void adminCanInviteMembersAndAdminsButNotOwners() throws Exception {
        invite(adminCookie, uniqueEmail(), "member");
        invite(adminCookie, uniqueEmail(), "admin");

        mvc.perform(json(post("/api/workspaces/" + workspaceId + "/invitations"),
                        "{\"email\":\"" + uniqueEmail() + "\",\"role\":\"owner\"}").cookie(adminCookie).with(xsrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void invitingSendsOneEmailWithTheLinkData() throws Exception {
        String email = uniqueEmail();

        String invitationId = JsonPath.read(invite(ownerCookie, email.toUpperCase(), "member"), "$.id");

        ArgumentCaptor<InvitationNotifier.Notice> notice = ArgumentCaptor.forClass(InvitationNotifier.Notice.class);
        verify(invitationNotifier, org.mockito.Mockito.atLeastOnce()).invited(notice.capture());
        assertThat(notice.getAllValues()).filteredOn(n -> n.invitationId().toString().equals(invitationId)).singleElement()
                .satisfies(n -> {
                    assertThat(n.recipientEmail()).isEqualTo(email);
                    assertThat(n.role()).isEqualTo("member");
                    assertThat(n.workspaceName()).startsWith("Acme ");
                });
    }

    @Test
    void duplicateAndInvalidInvitationsAreRejected() throws Exception {
        String email = uniqueEmail();
        invite(ownerCookie, email, "member");

        mvc.perform(json(post("/api/workspaces/" + workspaceId + "/invitations"),
                        "{\"email\":\"" + email + "\",\"role\":\"admin\"}").cookie(ownerCookie).with(xsrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invitation.already_pending"));
        mvc.perform(json(post("/api/workspaces/" + workspaceId + "/invitations"),
                        "{\"email\":\"" + memberEmail + "\",\"role\":\"member\"}").cookie(ownerCookie).with(xsrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invitation.already_member"));
        mvc.perform(json(post("/api/workspaces/" + workspaceId + "/invitations"),
                        "{\"email\":\"nope\",\"role\":\"member\"}").cookie(ownerCookie).with(xsrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/workspaces/" + workspaceId + "/invitations"),
                        "{\"email\":\"" + uniqueEmail() + "\",\"role\":\"emperor\"}").cookie(ownerCookie).with(xsrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("role.invalid"));
    }

    @Test
    void adminsSeeAndCanRevokePendingInvitations() throws Exception {
        String email = uniqueEmail();
        String invitationId = JsonPath.read(invite(ownerCookie, email, "member"), "$.id");

        mvc.perform(get("/api/workspaces/" + workspaceId + "/invitations").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + invitationId + "')].email").value(email));

        mvc.perform(delete("/api/workspaces/" + workspaceId + "/invitations/" + invitationId).cookie(memberCookie).with(xsrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/workspaces/" + workspaceId + "/invitations/" + invitationId).cookie(adminCookie).with(xsrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/workspaces/" + workspaceId + "/invitations").cookie(adminCookie))
                .andExpect(jsonPath("$[?(@.id=='" + invitationId + "')]").isEmpty());
    }

    // ---- accepting ---------------------------------------------------------------------------------------

    @Test
    void anInvitationCanOnlyBeAcceptedByTheInvitedEmailAndOnlyOnce() throws Exception {
        String inviteeEmail = uniqueEmail();
        Cookie invitee = login(inviteeEmail);
        String invitationId = JsonPath.read(invite(ownerCookie, inviteeEmail, "admin"), "$.id");

        // A different signed-in user cannot take it, and cannot tell it exists.
        mvc.perform(post("/api/invitations/" + invitationId + "/accept").cookie(outsiderCookie).with(xsrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("invitation.not_found"));
        mvc.perform(post("/api/invitations/" + UUID.randomUUID() + "/accept").cookie(outsiderCookie).with(xsrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("invitation.not_found"));

        mvc.perform(get("/api/invitations").cookie(invitee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].workspaceId").value(workspaceId))
                .andExpect(jsonPath("$[0].role").value("admin"));
        mvc.perform(get("/api/invitations").cookie(outsiderCookie)).andExpect(jsonPath("$").isEmpty());

        mvc.perform(post("/api/invitations/" + invitationId + "/accept").cookie(invitee).with(xsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workspaceId))
                .andExpect(jsonPath("$.role").value("admin"));
        mvc.perform(get("/api/workspaces/" + workspaceId).cookie(invitee)).andExpect(status().isOk());
        mvc.perform(post("/api/invitations/" + invitationId + "/accept").cookie(invitee).with(xsrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/invitations").cookie(invitee)).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void aRevokedInvitationCannotBeAccepted() throws Exception {
        String inviteeEmail = uniqueEmail();
        Cookie invitee = login(inviteeEmail);
        String invitationId = JsonPath.read(invite(ownerCookie, inviteeEmail, "member"), "$.id");
        mvc.perform(delete("/api/workspaces/" + workspaceId + "/invitations/" + invitationId).cookie(ownerCookie).with(xsrf()))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/invitations/" + invitationId + "/accept").cookie(invitee).with(xsrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/workspaces/" + workspaceId).cookie(invitee)).andExpect(status().isNotFound());
    }

    // ---- changing roles and removing members ----------------------------------------------------------

    @Test
    void ownerCanChangeRolesAndTheChangeShowsInTheList() throws Exception {
        mvc.perform(json(patch("/api/workspaces/" + workspaceId + "/members/" + memberId), role("admin"))
                        .cookie(ownerCookie).with(xsrf()))
                .andExpect(status().isNoContent());

        assertThat(roleOf(memberId)).isEqualTo("admin");
    }

    @Test
    void aPlainMemberCannotChangeAnyRole() throws Exception {
        mvc.perform(json(patch("/api/workspaces/" + workspaceId + "/members/" + memberId), role("admin"))
                        .cookie(memberCookie).with(xsrf()))
                .andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/workspaces/" + workspaceId + "/members/" + ownerId), role("member"))
                        .cookie(memberCookie).with(xsrf()))
                .andExpect(status().isForbidden());

        assertThat(roleOf(memberId)).isEqualTo("member");
        assertThat(roleOf(ownerId)).isEqualTo("owner");
    }

    @Test
    void anAdminCannotTouchOwnersOrCreateNewOnes() throws Exception {
        mvc.perform(json(patch("/api/workspaces/" + workspaceId + "/members/" + ownerId), role("member"))
                        .cookie(adminCookie).with(xsrf()))
                .andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/workspaces/" + workspaceId + "/members/" + memberId), role("owner"))
                        .cookie(adminCookie).with(xsrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/workspaces/" + workspaceId + "/members/" + ownerId).cookie(adminCookie).with(xsrf()))
                .andExpect(status().isForbidden());

        assertThat(roleOf(ownerId)).isEqualTo("owner");
        assertThat(roleOf(memberId)).isEqualTo("member");
    }

    @Test
    void theLastOwnerCannotBeDemotedOrRemoved() throws Exception {
        mvc.perform(json(patch("/api/workspaces/" + workspaceId + "/members/" + ownerId), role("admin"))
                        .cookie(ownerCookie).with(xsrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("workspace.last_owner"));
        mvc.perform(delete("/api/workspaces/" + workspaceId + "/members/" + ownerId).cookie(ownerCookie).with(xsrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("workspace.last_owner"));

        assertThat(roleOf(ownerId)).isEqualTo("owner");
    }

    @Test
    void anOwnerCanLeaveOnceAnotherOwnerExists() throws Exception {
        mvc.perform(json(patch("/api/workspaces/" + workspaceId + "/members/" + adminId), role("owner"))
                        .cookie(ownerCookie).with(xsrf()))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/api/workspaces/" + workspaceId + "/members/" + ownerId).cookie(ownerCookie).with(xsrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/workspaces/" + workspaceId).cookie(ownerCookie)).andExpect(status().isNotFound());
        mvc.perform(get("/api/workspaces/" + workspaceId).cookie(adminCookie)).andExpect(status().isOk());
    }

    @Test
    void aRemovedMemberLosesAccessImmediately() throws Exception {
        mvc.perform(get("/api/workspaces/" + workspaceId + "/members").cookie(memberCookie)).andExpect(status().isOk());

        mvc.perform(delete("/api/workspaces/" + workspaceId + "/members/" + memberId).cookie(adminCookie).with(xsrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/workspaces/" + workspaceId).cookie(memberCookie)).andExpect(status().isNotFound());
        mvc.perform(get("/api/workspaces/" + workspaceId + "/members").cookie(memberCookie)).andExpect(status().isNotFound());
        mvc.perform(get("/api/workspaces").cookie(memberCookie)).andExpect(jsonPath("$[?(@.id=='" + workspaceId + "')]").isEmpty());
    }

    @Test
    void aMemberCanLeaveButNotRemoveOthers() throws Exception {
        mvc.perform(delete("/api/workspaces/" + workspaceId + "/members/" + adminId).cookie(memberCookie).with(xsrf()))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/workspaces/" + workspaceId + "/members/" + memberId).cookie(memberCookie).with(xsrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/workspaces/" + workspaceId).cookie(memberCookie)).andExpect(status().isNotFound());
    }

    @Test
    void anUnknownRoleOrMemberIsHandledGracefully() throws Exception {
        mvc.perform(json(patch("/api/workspaces/" + workspaceId + "/members/" + memberId), role("emperor"))
                        .cookie(ownerCookie).with(xsrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(json(patch("/api/workspaces/" + workspaceId + "/members/" + UUID.randomUUID()), role("admin"))
                        .cookie(ownerCookie).with(xsrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("member.not_found"));
    }

    @Test
    void writesNeedACsrfTokenWhenUsingTheSessionCookie() throws Exception {
        mvc.perform(json(patch("/api/workspaces/" + workspaceId + "/members/" + memberId), role("admin")).cookie(ownerCookie))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/workspaces/" + workspaceId + "/members/" + memberId).cookie(ownerCookie))
                .andExpect(status().isForbidden());

        assertThat(roleOf(memberId)).isEqualTo("member");
    }
}
