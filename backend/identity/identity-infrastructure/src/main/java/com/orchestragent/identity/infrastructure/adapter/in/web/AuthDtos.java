package com.orchestragent.identity.infrastructure.adapter.in.web;

import com.orchestragent.identity.application.ApiTokenService.ApiTokenInfo;
import com.orchestragent.identity.application.ApiTokenService.CreatedApiToken;
import com.orchestragent.identity.contract.UserView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Wire format of the identity REST API. */
final class AuthDtos {

    private AuthDtos() {
    }

    record RequestCodeRequest(@NotBlank @Size(max = 254) String email) {
    }

    record VerifyCodeRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Pattern(regexp = "\\d{6}") String code) {
    }

    record UserResponse(UUID id, String email, String name) {

        static UserResponse from(UserView user) {
            return new UserResponse(user.id(), user.email(), user.name());
        }
    }

    record CreateTokenRequest(@NotBlank @Size(max = 100) String name) {
    }

    record ApiTokenResponse(
            UUID id, String name, String tokenPrefix, Instant lastUsedAt, Instant revokedAt, Instant createdAt) {

        static ApiTokenResponse from(ApiTokenInfo info) {
            return new ApiTokenResponse(
                    info.id(), info.name(), info.tokenPrefix(), info.lastUsedAt(), info.revokedAt(), info.createdAt());
        }
    }

    /** The only response that ever contains the raw token. */
    record CreatedApiTokenResponse(ApiTokenResponse token, String secret) {

        static CreatedApiTokenResponse from(CreatedApiToken created) {
            return new CreatedApiTokenResponse(ApiTokenResponse.from(created.info()), created.rawToken());
        }
    }
}
