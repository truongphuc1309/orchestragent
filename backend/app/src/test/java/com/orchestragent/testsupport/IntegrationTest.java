package com.orchestragent.testsupport;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.orchestragent.PostgresTestConfiguration;
import com.orchestragent.identity.application.port.LoginCodeGenerator;
import com.orchestragent.identity.application.port.LoginCodeSender;
import com.orchestragent.workspace.application.port.InvitationNotifier;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Shared setup of the API integration tests: real Postgres and real security chains, but mock outgoing email
 * and a fixed login code so tests know the code. All subclasses use the same Spring configuration, so the
 * application context (and its Postgres container) is created once.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
public abstract class IntegrationTest {

    /** Fixed so tests can check that the code never shows up in logs or in the database. */
    protected static final String CODE = "907314";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcClient jdbc;

    @MockitoBean
    protected LoginCodeSender sender;

    @MockitoBean
    protected LoginCodeGenerator generator;

    @MockitoBean
    protected InvitationNotifier invitationNotifier;

    /**
     * The CSRF protection exactly as the browser app uses it: take the {@code XSRF-TOKEN} cookie the server
     * hands out and send its value back in the {@code X-XSRF-TOKEN} header. (Spring Security's own
     * {@code csrf()} test helper is avoided on purpose: it swaps the token repository for the whole shared
     * context, which would hide real CSRF failures in other tests.)
     */
    protected RequestPostProcessor xsrf() throws Exception {
        Cookie token = mvc.perform(get("/api/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request -> {
            Cookie[] existing = request.getCookies() == null ? new Cookie[0] : request.getCookies();
            Cookie[] all = Arrays.copyOf(existing, existing.length + 1);
            all[existing.length] = token;
            request.setCookies(all);
            request.addHeader("X-XSRF-TOKEN", token.getValue());
            return request;
        };
    }

    protected void fixCode() {
        given(generator.generate()).willReturn(CODE);
    }

    /** Each test uses its own address, so tests do not rate-limit each other. */
    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected ResultActions requestCode(String email) throws Exception {
        return mvc.perform(json(post("/api/auth/code"), "{\"email\":\"" + email + "\"}").with(xsrf()));
    }

    protected ResultActions verifyCode(String email, String code) throws Exception {
        return mvc.perform(
                json(post("/api/auth/verify"), "{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}").with(xsrf()));
    }

    /** Runs the whole login flow and returns the session cookie. */
    protected Cookie login(String email) throws Exception {
        fixCode();
        requestCode(email).andReturn();
        return verifyCode(email, CODE).andReturn().getResponse().getCookie("oa_session");
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType("application/json").content(body);
    }
}
