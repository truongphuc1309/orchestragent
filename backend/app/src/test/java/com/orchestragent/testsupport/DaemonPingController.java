package com.orchestragent.testsupport;

import com.orchestragent.platform.security.CurrentUser;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stand-in for the real daemon endpoints (step 8), so the daemon security chain can be tested now. */
@RestController
class DaemonPingController {

    @GetMapping("/api/daemon/ping")
    Map<String, String> ping(CurrentUser user) {
        return Map.of("userId", user.id().toString());
    }
}
