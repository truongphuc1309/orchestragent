package com.orchestragent.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

class ApiTokenIntegrationTest extends IdentityIntegrationTest {

    private String createToken(Cookie session, String name) throws Exception {
        return mvc.perform(json(post("/api/me/tokens"), "{\"name\":\"" + name + "\"}").cookie(session).with(xsrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void createdTokenIsShownOnceAndListedWithoutItsSecret() throws Exception {
        Cookie session = login(uniqueEmail());

        String created = createToken(session, "laptop");
        String secret = JsonPath.read(created, "$.secret");
        String id = JsonPath.read(created, "$.token.id");
        assertThat(secret).startsWith("oat_");
        assertThat((String) JsonPath.read(created, "$.token.tokenPrefix")).isEqualTo(secret.substring(0, 10));

        String listed = mvc.perform(get("/api/me/tokens").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].name").value("laptop"))
                .andReturn().getResponse().getContentAsString();
        assertThat(listed).doesNotContain(secret).doesNotContain("secret").doesNotContain("tokenHash");
    }

    @Test
    void onlyTheHashOfTheTokenIsStored() throws Exception {
        Cookie session = login(uniqueEmail());
        String secret = JsonPath.read(createToken(session, "laptop"), "$.secret");

        String stored = jdbc.sql("SELECT token_hash FROM identity.api_tokens WHERE token_prefix = :p")
                .param("p", secret.substring(0, 10)).query(String.class).single();

        assertThat(stored).hasSize(64).doesNotContain(secret);
    }

    @Test
    void validTokenAuthenticatesOnTheDaemonApiAndRecordsUse() throws Exception {
        Cookie session = login(uniqueEmail());
        String userId = JsonPath.read(
                mvc.perform(get("/api/me").cookie(session)).andReturn().getResponse().getContentAsString(), "$.id");
        String created = createToken(session, "laptop");
        String secret = JsonPath.read(created, "$.secret");

        mvc.perform(get("/api/daemon/ping").header("Authorization", "Bearer " + secret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId));

        mvc.perform(get("/api/me/tokens").cookie(session)).andExpect(jsonPath("$[0].lastUsedAt").isNotEmpty());
    }

    @Test
    void daemonApiRejectsMissingUnknownAndMalformedCredentials() throws Exception {
        mvc.perform(get("/api/daemon/ping")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/daemon/ping").header("Authorization", "Bearer oat_doesnotexist")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/daemon/ping").header("Authorization", "Bearer ")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/daemon/ping").header("Authorization", "Basic abc")).andExpect(status().isUnauthorized());
    }

    @Test
    void daemonApiNeverAcceptsTheBrowserSessionCookieOrAJwt() throws Exception {
        Cookie session = login(uniqueEmail());

        mvc.perform(get("/api/daemon/ping").cookie(session)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/daemon/ping").header("Authorization", "Bearer " + session.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiTokenDoesNotWorkOnTheBrowserApi() throws Exception {
        Cookie session = login(uniqueEmail());
        String secret = JsonPath.read(createToken(session, "laptop"), "$.secret");

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + secret)).andExpect(status().isUnauthorized());
    }

    @Test
    void revokedTokenIsRejectedImmediately() throws Exception {
        Cookie session = login(uniqueEmail());
        String created = createToken(session, "laptop");
        String secret = JsonPath.read(created, "$.secret");
        String id = JsonPath.read(created, "$.token.id");
        mvc.perform(get("/api/daemon/ping").header("Authorization", "Bearer " + secret)).andExpect(status().isOk());

        mvc.perform(delete("/api/me/tokens/" + id).cookie(session).with(xsrf())).andExpect(status().isNoContent());

        mvc.perform(get("/api/daemon/ping").header("Authorization", "Bearer " + secret)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/tokens").cookie(session)).andExpect(jsonPath("$[0].revokedAt").isNotEmpty());
    }

    @Test
    void usersCannotSeeOrRevokeEachOthersTokens() throws Exception {
        Cookie ada = login(uniqueEmail());
        Cookie grace = login(uniqueEmail());
        String created = createToken(ada, "ada's token");
        String secret = JsonPath.read(created, "$.secret");
        String id = JsonPath.read(created, "$.token.id");

        mvc.perform(get("/api/me/tokens").cookie(grace)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(delete("/api/me/tokens/" + id).cookie(grace).with(xsrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/daemon/ping").header("Authorization", "Bearer " + secret)).andExpect(status().isOk());
    }

    @Test
    void tokenNameIsValidated() throws Exception {
        Cookie session = login(uniqueEmail());

        mvc.perform(json(post("/api/me/tokens"), "{\"name\":\"   \"}").cookie(session).with(xsrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/me/tokens"), "{\"name\":\"" + "x".repeat(101) + "\"}").cookie(session).with(xsrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokensRequireLogin() throws Exception {
        mvc.perform(get("/api/me/tokens")).andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/me/tokens"), "{\"name\":\"x\"}").with(xsrf())).andExpect(status().isUnauthorized());
    }
}
