package com.orchestragent.identity.infrastructure.adapter.in.web;

import com.orchestragent.identity.application.ProfileService;
import com.orchestragent.identity.application.ProfileService.AvatarPicture;
import com.orchestragent.platform.security.CurrentUser;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves avatar pictures to signed-in users. The storage stays private: pictures always go through this endpoint.
 * The address contains the avatar ID, which changes with every upload, so a picture never changes under its address
 * and the browser may keep it for good.
 */
@RestController
class AvatarController {

    private static final CacheControl FOREVER = CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable();

    private final ProfileService profiles;

    AvatarController(ProfileService profiles) {
        this.profiles = profiles;
    }

    /** {@code viewer} is unused, but asking for it makes the endpoint reject requests without a signed-in user. */
    @GetMapping("/api/users/{userId}/avatar/{avatarId}")
    ResponseEntity<byte[]> avatar(CurrentUser viewer, @PathVariable UUID userId, @PathVariable UUID avatarId) {
        AvatarPicture picture = profiles.avatar(userId, avatarId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(picture.mediaType()))
                .cacheControl(FOREVER)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .body(picture.content());
    }
}
