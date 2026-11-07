package com.orchestragent.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.orchestragent.identity.domain.Email;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.web.servlet.MvcResult;

class LoginFlowIntegrationTest extends IdentityIntegrationTest {

    @Autowired
    JwtEncoder jwtEncoder;

    @Test
    void fullLoginFlowSetsAnHttpOnlySessionCookieThatAuthenticatesMe() throws Exception {
        String email = uniqueEmail();
        fixCode();

        requestCode(email).andExpect(status().isNoContent());
        verify(sender).send(eq(new Email(email)), eq(CODE), any(Instant.class));

        MvcResult verified = verifyCode(email, CODE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(cookie().httpOnly("oa_session", true))
                .andExpect(cookie().path("oa_session", "/"))
                .andExpect(cookie().sameSite("oa_session", "Lax"))
                .andReturn();

        Cookie session = verified.getResponse().getCookie("oa_session");
        assertThat(session).isNotNull();
        assertThat(session.getMaxAge()).isBetween((int) Duration.ofDays(7).minusMinutes(1).toSeconds(), (int) Duration.ofDays(7).toSeconds());
        assertThat(verified.getResponse().getContentAsString()).doesNotContain(session.getValue());

        mvc.perform(get("/api/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value(email.substring(0, email.indexOf('@'))));
    }

    @Test
    void emailIsNormalizedSoAnyCaseLogsInToTheSameUser() throws Exception {
        String email = uniqueEmail();
        fixCode();
        requestCode("  " + email.toUpperCase() + " ").andExpect(status().isNoContent());

        verifyCode(email, CODE).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void secondLoginReturnsTheSameUser() throws Exception {
        String email = uniqueEmail();
        Cookie first = login(email);
        String firstId = JsonPath.read(
                mvc.perform(get("/api/me").cookie(first)).andReturn().getResponse().getContentAsString(), "$.id");

        // A new code needs the resend interval to pass; age the first one in the database instead of sleeping.
        jdbc.sql("UPDATE identity.login_codes SET created_at = created_at - interval '2 minutes' WHERE email = :e")
                .param("e", email).update();
        Cookie second = login(email);

        mvc.perform(get("/api/me").cookie(second)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(firstId));
        assertThat(jdbc.sql("SELECT count(*) FROM identity.users WHERE email = :e").param("e", email)
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void secondCodeRequestWithinAMinuteIsRateLimited() throws Exception {
        String email = uniqueEmail();
        fixCode();
        requestCode(email).andExpect(status().isNoContent());

        requestCode(email)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("login.code_rate_limited"));
    }

    @Test
    void codeIsStoredOnlyAsAHash() throws Exception {
        String email = uniqueEmail();
        fixCode();
        requestCode(email).andExpect(status().isNoContent());

        String stored = jdbc.sql("SELECT code_hash FROM identity.login_codes WHERE email = :e").param("e", email)
                .query(String.class).single();

        assertThat(stored).hasSize(64).doesNotContain(CODE);
    }

    @Test
    void wrongGuessesAreCountedAndFiveOfThemLockTheCodeEvenForTheRightAnswer() throws Exception {
        String email = uniqueEmail();
        fixCode();
        requestCode(email).andExpect(status().isNoContent());

        for (int i = 0; i < 5; i++) {
            verifyCode(email, "000000").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("login.invalid_code"));
        }

        // The counter survived the failed requests (no rollback), so even the right code is now refused.
        assertThat(jdbc.sql("SELECT attempts FROM identity.login_codes WHERE email = :e").param("e", email)
                .query(Integer.class).single()).isEqualTo(5);
        verifyCode(email, CODE).andExpect(status().isUnauthorized());
    }

    @Test
    void codeCanBeUsedOnlyOnce() throws Exception {
        String email = uniqueEmail();
        login(email);

        verifyCode(email, CODE).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredCodeIsRefused() throws Exception {
        String email = uniqueEmail();
        fixCode();
        requestCode(email).andExpect(status().isNoContent());
        jdbc.sql("UPDATE identity.login_codes SET expires_at = now() - interval '1 second' WHERE email = :e")
                .param("e", email).update();

        verifyCode(email, CODE).andExpect(status().isUnauthorized());
    }

    @Test
    void verifyWithoutEverRequestingACodeIsRefused() throws Exception {
        verifyCode(uniqueEmail(), "123456").andExpect(status().isUnauthorized());
    }

    @Test
    void malformedInputIsRejectedWithProblemDetails() throws Exception {
        mvc.perform(json(post("/api/auth/code"), "{\"email\":\"nope\"}").with(xsrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("email.invalid"));
        mvc.perform(json(post("/api/auth/verify"), "{\"email\":\"a@b.co\",\"code\":\"12\"}").with(xsrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/auth/verify"), "{\"email\":\"a@b.co\"}").with(xsrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsTheCookie() throws Exception {
        mvc.perform(post("/api/auth/logout").with(xsrf()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("oa_session", 0))
                .andExpect(cookie().httpOnly("oa_session", true));
    }

    @Test
    void tamperedExpiredOrForeignTokensAreRefused() throws Exception {
        Cookie valid = login(uniqueEmail());
        String token = valid.getValue();
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");

        mvc.perform(get("/api/me").cookie(new Cookie("oa_session", tampered))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(new Cookie("oa_session", "garbage"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(new Cookie("oa_session", signedToken(Instant.now().minusSeconds(3600), "orchestragent"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(new Cookie("oa_session", signedToken(Instant.now().plusSeconds(3600), "someone-else"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staleSessionCookieDoesNotBlockLoggingInAgain() throws Exception {
        String email = uniqueEmail();
        fixCode();

        mvc.perform(json(post("/api/auth/code"), "{\"email\":\"" + email + "\"}")
                        .cookie(new Cookie("oa_session", "expired-or-garbage")).with(xsrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void bearerHeaderAlsoAuthenticatesAndNeedsNoCsrfToken() throws Exception {
        Cookie session = login(uniqueEmail());

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + session.getValue())).andExpect(status().isOk());
        mvc.perform(json(post("/api/me/tokens"), "{\"name\":\"cli\"}").header("Authorization", "Bearer " + session.getValue()))
                .andExpect(status().isCreated());
    }

    @Test
    void cookieAuthenticatedWritesRequireTheCsrfToken() throws Exception {
        Cookie session = login(uniqueEmail());

        // Regression test: the session cookie must never exempt a request from CSRF protection.
        mvc.perform(json(post("/api/me/tokens"), "{\"name\":\"cli\"}").cookie(session)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/me/tokens/" + UUID.randomUUID()).cookie(session)).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/me/tokens"), "{\"name\":\"cli\"}").cookie(session).with(xsrf()))
                .andExpect(status().isCreated());
    }

    @Test
    void csrfCookieIsIssuedAndItsValueIsAcceptedAsTheHeader() throws Exception {
        Cookie xsrf = mvc.perform(get("/api/health")).andExpect(cookie().exists("XSRF-TOKEN")).andReturn()
                .getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.isHttpOnly()).isFalse();
        String email = uniqueEmail();
        fixCode();

        mvc.perform(json(post("/api/auth/code"), "{\"email\":\"" + email + "\"}").cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isNoContent());
        mvc.perform(json(post("/api/auth/code"), "{\"email\":\"" + uniqueEmail() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedRequestsDoNotRotateTheCsrfToken() throws Exception {
        Cookie session = login(uniqueEmail());
        Cookie xsrf = mvc.perform(get("/api/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");

        // The browser already holds a token, so the server must not hand out a different one.
        mvc.perform(get("/api/me").cookie(session, xsrf))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("XSRF-TOKEN"));
    }

    @Test
    void loginCodeNeverAppearsInLogs(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        login(email);
        verifyCode(email, "000000").andExpect(status().isUnauthorized());

        assertThat(output.getAll()).doesNotContain(CODE);
    }

    private String signedToken(Instant expiresAt, String issuer) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(UUID.randomUUID().toString())
                .issuedAt(expiresAt.minusSeconds(7200))
                .expiresAt(expiresAt)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
