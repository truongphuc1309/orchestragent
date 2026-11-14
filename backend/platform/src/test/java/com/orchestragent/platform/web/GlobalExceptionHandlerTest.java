package com.orchestragent.platform.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.ForbiddenException;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.shared.error.RateLimitedException;
import com.orchestragent.shared.error.UnauthorizedException;
import com.orchestragent.shared.error.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void notFoundMapsTo404() throws Exception {
        mvc.perform(get("/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("issue.not_found"))
                .andExpect(jsonPath("$.detail").value("Issue 7 does not exist"));
    }

    @Test
    void forbiddenMapsTo403() throws Exception {
        mvc.perform(get("/forbidden")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void conflictMapsTo409() throws Exception {
        mvc.perform(get("/conflict")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("conflict"));
    }

    @Test
    void validationMapsTo400() throws Exception {
        mvc.perform(get("/validation")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
    }

    @Test
    void unauthorizedMapsTo401() throws Exception {
        mvc.perform(get("/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void rateLimitedMapsTo429WithRetryAfter() throws Exception {
        mvc.perform(get("/rate-limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("rate_limited"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(30));
    }

    @Test
    void constraintViolationsMapTo409WithoutLeakingSql() throws Exception {
        mvc.perform(get("/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("members_pkey"))));
    }

    @Test
    void aLostRaceOnTheSameRowMapsTo409AndAsksToTryAgain() throws Exception {
        mvc.perform(get("/optimistic"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("try again")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("agent_tasks"))));
    }

    @Test
    void unexpectedErrorsDoNotLeakDetails() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    void unavailableStorageMapsTo503WithoutDetails() throws Exception {
        mvc.perform(get("/storage-down"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("storage.unavailable"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("9000"))));
    }

    @Test
    void tooLargeUploadMapsTo413() throws Exception {
        mvc.perform(get("/too-large"))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.code").value("upload.too_large"));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("issue.not_found", "Issue 7 does not exist");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new ForbiddenException("Not allowed");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("Already exists");
        }

        @GetMapping("/validation")
        void validation() {
            throw new ValidationException("Bad input");
        }

        @GetMapping("/unauthorized")
        void unauthorized() {
            throw new UnauthorizedException("Invalid code");
        }

        @GetMapping("/rate-limited")
        void rateLimited() {
            throw new RateLimitedException("Too often", 30);
        }

        @GetMapping("/integrity")
        void integrity() {
            throw new org.springframework.dao.DataIntegrityViolationException(
                    "duplicate key value violates unique constraint \"members_pkey\"");
        }

        @GetMapping("/optimistic")
        void optimistic() {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException("agent_tasks", "some-id");
        }

        @GetMapping("/storage-down")
        void storageDown() {
            throw new com.orchestragent.platform.storage.StorageUnavailableException(
                    "Object storage is not available", new java.io.IOException("Connection refused: localhost:9000"));
        }

        @GetMapping("/too-large")
        void tooLarge() {
            throw new org.springframework.web.multipart.MaxUploadSizeExceededException(2 * 1024 * 1024);
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("secret database password is hunter2");
        }
    }
}
