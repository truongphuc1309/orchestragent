package com.orchestragent.agent.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.shared.error.ValidationException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private final UUID workspace = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();

    private Runtime newRuntime() {
        return Runtime.register(workspace, owner, "daemon-1", "Ada's laptop", "claude", "1.2.3", T0);
    }

    @Test
    void aNewRuntimeIsOnlineWithItsFirstHeartbeat() {
        Runtime runtime = newRuntime();

        assertThat(runtime.status()).isEqualTo(RuntimeStatus.ONLINE);
        assertThat(runtime.lastHeartbeatAt()).isEqualTo(T0);
        assertThat(runtime.workspaceId()).isEqualTo(workspace);
        assertThat(runtime.ownerUserId()).isEqualTo(owner);
        assertThat(runtime.daemonId()).isEqualTo("daemon-1");
        assertThat(runtime.deviceName()).isEqualTo("Ada's laptop");
        assertThat(runtime.provider()).isEqualTo("claude");
        assertThat(runtime.providerVersion()).isEqualTo("1.2.3");
    }

    @Test
    void aHeartbeatBringsAnOfflineRuntimeBackOnline() {
        Runtime runtime = newRuntime();
        runtime.markOffline();

        runtime.heartbeat(T0.plusSeconds(30));

        assertThat(runtime.status()).isEqualTo(RuntimeStatus.ONLINE);
        assertThat(runtime.lastHeartbeatAt()).isEqualTo(T0.plusSeconds(30));
    }

    @Test
    void markingOfflineReportsWhetherItWasOnline() {
        Runtime runtime = newRuntime();

        assertThat(runtime.markOffline()).isTrue();
        assertThat(runtime.status()).isEqualTo(RuntimeStatus.OFFLINE);
        assertThat(runtime.markOffline()).isFalse();
    }

    @Test
    void registeringAgainRefreshesDeviceAndVersionAndGoesOnline() {
        Runtime runtime = newRuntime();
        runtime.markOffline();

        runtime.reregister("New laptop", "2.0.0", T0.plusSeconds(5));

        assertThat(runtime.status()).isEqualTo(RuntimeStatus.ONLINE);
        assertThat(runtime.deviceName()).isEqualTo("New laptop");
        assertThat(runtime.providerVersion()).isEqualTo("2.0.0");
        assertThat(runtime.lastHeartbeatAt()).isEqualTo(T0.plusSeconds(5));
    }

    @Test
    void isStaleOnlyAfterNinetySecondsOfSilence() {
        Runtime runtime = newRuntime();

        assertThat(Runtime.OFFLINE_AFTER).isEqualTo(Duration.ofSeconds(90));
        assertThat(runtime.isStale(T0.plusSeconds(89))).isFalse();
        assertThat(runtime.isStale(T0.plusSeconds(90))).as("exactly 90 s is still fine").isFalse();
        assertThat(runtime.isStale(T0.plusSeconds(91))).isTrue();
    }

    @Test
    void anOfflineRuntimeIsNotStale() {
        Runtime runtime = newRuntime();
        runtime.markOffline();

        assertThat(runtime.isStale(T0.plus(Duration.ofDays(1)))).isFalse();
    }

    @Test
    void aHeartbeatResetsTheSilence() {
        Runtime runtime = newRuntime();

        runtime.heartbeat(T0.plusSeconds(80));

        assertThat(runtime.isStale(T0.plusSeconds(150))).isFalse();
        assertThat(runtime.isStale(T0.plusSeconds(171))).isTrue();
    }

    @Test
    void trimsTextAndAllowsAnEmptyVersion() {
        Runtime runtime = Runtime.register(workspace, owner, "  d  ", "  Laptop ", " mock ", null, T0);

        assertThat(runtime.daemonId()).isEqualTo("d");
        assertThat(runtime.deviceName()).isEqualTo("Laptop");
        assertThat(runtime.provider()).isEqualTo("mock");
        assertThat(runtime.providerVersion()).isEmpty();
    }

    @Test
    void rejectsInvalidIdentifyingText() {
        assertThatThrownBy(() -> Runtime.validDaemonId(" ")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Runtime.validDaemonId("x".repeat(101))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Runtime.validDeviceName("two\nlines")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Runtime.validDeviceName(null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Runtime.validVersion("x".repeat(101))).isInstanceOf(ValidationException.class);
        assertThat(Runtime.validDeviceName("x".repeat(100))).hasSize(100);
    }

    @Test
    void providerIdsAreShortLowerCaseCodes() {
        assertThat(Runtime.validProvider("claude")).isEqualTo("claude");
        assertThat(Runtime.validProvider("my-tool_2.0")).isEqualTo("my-tool_2.0");
        for (String bad : new String[] {"", " ", "Claude", "-start", "has space", "semi;colon", "x".repeat(51)}) {
            assertThatThrownBy(() -> Runtime.validProvider(bad)).as(bad).isInstanceOf(ValidationException.class);
        }
        assertThatThrownBy(() -> Runtime.validProvider(null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void statusCodesAreLowerCase() {
        assertThat(RuntimeStatus.ONLINE.code()).isEqualTo("online");
        assertThat(RuntimeStatus.fromCode("offline")).isEqualTo(RuntimeStatus.OFFLINE);
    }
}
