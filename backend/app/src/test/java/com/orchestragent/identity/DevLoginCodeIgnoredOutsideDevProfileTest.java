package com.orchestragent.identity;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** DEV_LOGIN_CODE is configured here, but without the "dev" profile it must have no effect at all. */
@TestPropertySource(properties = "orchestragent.dev-login-code=424242")
class DevLoginCodeIgnoredOutsideDevProfileTest extends IdentityIntegrationTest {

    @Test
    void devCodeIsRejectedWhenTheDevProfileIsNotActive() throws Exception {
        verifyCode(uniqueEmail(), "424242").andExpect(status().isUnauthorized());
    }
}
