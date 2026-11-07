package com.orchestragent.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = "orchestragent.dev-login-code=424242")
class DevLoginCodeInDevProfileTest extends IdentityIntegrationTest {

    @Test
    void devCodeLogsInAnyEmailWithoutRequestingACode() throws Exception {
        String email = uniqueEmail();

        Cookie session = verifyCode(email, "424242")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn().getResponse().getCookie("oa_session");

        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk());
    }

    @Test
    void otherWrongCodesAreStillRejectedInDev() throws Exception {
        verifyCode(uniqueEmail(), "111111").andExpect(status().isUnauthorized());
    }
}
