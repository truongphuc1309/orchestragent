package com.orchestragent.identity;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/** The limit of login codes per client address. This class has its own context with a small limit. */
@TestPropertySource(properties = {
    "orchestragent.rate-limit.auth-code.max-requests=3",
    "orchestragent.rate-limit.auth-code.window=10m"
})
class AuthCodeRateLimitTest extends IdentityIntegrationTest {

    @Test
    void anAddressThatAsksTooOftenGets429WithRetryAfterAndNoMailIsSent() throws Exception {
        fixCode();
        String address = "203.0.113.10";
        for (int i = 0; i < 3; i++) {
            requestCodeFrom(address, uniqueEmail()).andExpect(status().isNoContent());
        }

        requestCodeFrom(address, uniqueEmail())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("login.ip_rate_limited"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());

        // Three mails for the three allowed requests, none for the fourth.
        verify(sender, times(3)).send(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void anotherAddressIsNotAffected() throws Exception {
        fixCode();
        for (int i = 0; i < 4; i++) {
            requestCodeFrom("203.0.113.20", uniqueEmail());
        }

        requestCodeFrom("203.0.113.21", uniqueEmail()).andExpect(status().isNoContent());
    }

    @Test
    void verifyingACodeIsNotLimitedByThisRule() throws Exception {
        fixCode();
        String email = uniqueEmail();
        requestCodeFrom("203.0.113.30", email).andExpect(status().isNoContent());
        for (int i = 0; i < 4; i++) {
            requestCodeFrom("203.0.113.30", uniqueEmail());
        }

        verifyCode(email, CODE).andExpect(status().isOk());
    }

    @Test
    void aRejectedRequestSendsNothingEvenForAKnownAddress() throws Exception {
        fixCode();
        String address = "203.0.113.40";
        for (int i = 0; i < 3; i++) {
            requestCodeFrom(address, uniqueEmail());
        }
        org.mockito.Mockito.clearInvocations(sender);

        requestCodeFrom(address, uniqueEmail()).andExpect(status().isTooManyRequests());

        verify(sender, never()).send(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    private ResultActions requestCodeFrom(String remoteAddress, String email) throws Exception {
        return mvc.perform(json(post("/api/auth/code"), "{\"email\":\"" + email + "\"}")
                .with(xsrf())
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                }));
    }
}
