package com.orchestragent.identity.infrastructure.adapter.in.web;

import com.orchestragent.identity.application.ApiTokenService;
import com.orchestragent.identity.application.GetMe;
import com.orchestragent.identity.application.ProfileService;
import com.orchestragent.identity.infrastructure.adapter.in.web.AuthDtos.ApiTokenResponse;
import com.orchestragent.identity.infrastructure.adapter.in.web.AuthDtos.CreateTokenRequest;
import com.orchestragent.identity.infrastructure.adapter.in.web.AuthDtos.CreatedApiTokenResponse;
import com.orchestragent.identity.infrastructure.adapter.in.web.AuthDtos.UpdateProfileRequest;
import com.orchestragent.identity.infrastructure.adapter.in.web.AuthDtos.UserResponse;
import com.orchestragent.platform.security.CurrentUser;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** The signed-in user, their profile and their API tokens. Every method works on the caller's own data only. */
@RestController
@RequestMapping("/api/me")
class MeController {

    private final GetMe getMe;
    private final ProfileService profiles;
    private final ApiTokenService apiTokens;

    MeController(GetMe getMe, ProfileService profiles, ApiTokenService apiTokens) {
        this.getMe = getMe;
        this.profiles = profiles;
        this.apiTokens = apiTokens;
    }

    @GetMapping
    UserResponse me(CurrentUser user) {
        return UserResponse.from(getMe.execute(user.id()));
    }

    @PatchMapping
    UserResponse updateProfile(CurrentUser user, @Valid @RequestBody UpdateProfileRequest request) {
        return UserResponse.from(profiles.update(user.id(), request.name(), request.bio()));
    }

    /** A multipart form with the picture in the part {@code file}. Size and format are checked by the server. */
    @PutMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    UserResponse changeAvatar(CurrentUser user, @RequestPart("file") MultipartFile file) throws IOException {
        return UserResponse.from(profiles.changeAvatar(user.id(), file.getBytes()));
    }

    @DeleteMapping("/avatar")
    UserResponse removeAvatar(CurrentUser user) {
        return UserResponse.from(profiles.removeAvatar(user.id()));
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
