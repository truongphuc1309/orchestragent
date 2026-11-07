package com.orchestragent.identity.infrastructure.adapter.in.web;

import com.orchestragent.identity.application.ApiTokenService;
import com.orchestragent.identity.application.GetMe;
import com.orchestragent.identity.infrastructure.adapter.in.web.AuthDtos.ApiTokenResponse;
import com.orchestragent.identity.infrastructure.adapter.in.web.AuthDtos.CreateTokenRequest;
import com.orchestragent.identity.infrastructure.adapter.in.web.AuthDtos.CreatedApiTokenResponse;
import com.orchestragent.identity.infrastructure.adapter.in.web.AuthDtos.UserResponse;
import com.orchestragent.platform.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user and their API tokens. Every method works on the caller's own data only. */
@RestController
@RequestMapping("/api/me")
class MeController {

    private final GetMe getMe;
    private final ApiTokenService apiTokens;

    MeController(GetMe getMe, ApiTokenService apiTokens) {
        this.getMe = getMe;
        this.apiTokens = apiTokens;
    }

    @GetMapping
    UserResponse me(CurrentUser user) {
        return UserResponse.from(getMe.execute(user.id()));
    }

    @GetMapping("/tokens")
    List<ApiTokenResponse> listTokens(CurrentUser user) {
        return apiTokens.list(user.id()).stream().map(ApiTokenResponse::from).toList();
    }

    @PostMapping("/tokens")
    ResponseEntity<CreatedApiTokenResponse> createToken(
            CurrentUser user, @Valid @RequestBody CreateTokenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CreatedApiTokenResponse.from(apiTokens.create(user.id(), request.name())));
    }

    @DeleteMapping("/tokens/{tokenId}")
    ResponseEntity<Void> revokeToken(CurrentUser user, @PathVariable UUID tokenId) {
        apiTokens.revoke(user.id(), tokenId);
        return ResponseEntity.noContent().build();
    }
}
