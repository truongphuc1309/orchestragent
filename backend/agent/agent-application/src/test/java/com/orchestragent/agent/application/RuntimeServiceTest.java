package com.orchestragent.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.agent.application.Fakes.FakeAccess;
import com.orchestragent.agent.application.Fakes.FakeUsers;
import com.orchestragent.agent.application.Fakes.InMemoryRuntimes;
import com.orchestragent.agent.application.Fakes.MutableClock;
import com.orchestragent.agent.application.RuntimeService.ProviderInfo;
import com.orchestragent.agent.application.Views.RuntimeView;
import com.orchestragent.agent.domain.Runtime;
import com.orchestragent.agent.domain.RuntimeId;
import com.orchestragent.agent.domain.RuntimeStatus;
import com.orchestragent.identity.contract.UserView;
import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.shared.error.ValidationException;
import com.orchestragent.workspace.contract.WorkspaceRole;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeServiceTest {

    private final InMemoryRuntimes runtimes = new InMemoryRuntimes();
    private final FakeAccess access = new FakeAccess();
    private final FakeUsers users = new FakeUsers();
    private final MutableClock clock = new MutableClock();
    private final RuntimeService service = new RuntimeService(runtimes, access, users, clock);

    private final UserView ada = users.add("Ada");
    private final UserView bob = users.add("Bob");
    private final UserView outsider = users.add("Otto");
    private final UUID workspace = UUID.randomUUID();

    {
        access.join(workspace, ada.id(), WorkspaceRole.MEMBER);
        access.join(workspace, bob.id(), WorkspaceRole.MEMBER);
    }

    private List<RuntimeView> registerAda(String daemon, ProviderInfo... providers) {
        return service.register(ada.id(), workspace, daemon, "Ada's laptop", List.of(providers));
    }

    private Runtime stored(RuntimeView view) {
        return runtimes.stored.get(new RuntimeId(view.id()));
    }

    // ---- register ----------------------------------------------------------------------------------------

    @Test
    void registersOneOnlineRuntimePerProvider() {
        List<RuntimeView> views = registerAda("d1", new ProviderInfo("claude", "1.0"), new ProviderInfo("mock", ""));

        assertThat(views).extracting(RuntimeView::provider).containsExactly("claude", "mock");
        assertThat(views).allSatisfy(view -> {
            assertThat(view.status()).isEqualTo(RuntimeStatus.ONLINE);
            assertThat(view.ownerId()).isEqualTo(ada.id());
            assertThat(view.ownerName()).isEqualTo("Ada");
            assertThat(view.deviceName()).isEqualTo("Ada's laptop");
            assertThat(view.workspaceId()).isEqualTo(workspace);
            assertThat(view.lastHeartbeatAt()).isEqualTo(clock.instant());
        });
        assertThat(views.getFirst().providerVersion()).isEqualTo("1.0");
        assertThat(runtimes.stored).hasSize(2);
    }

    @Test
    void registeringAgainReusesTheRuntimeAndBringsItBackOnline() {
        RuntimeView first = registerAda("d1", new ProviderInfo("claude", "1.0")).getFirst();
        clock.advance(Duration.ofMinutes(10));
        service.markStaleOffline();
        assertThat(stored(first).status()).isEqualTo(RuntimeStatus.OFFLINE);

        RuntimeView again = service.register(ada.id(), workspace, "d1", "Renamed laptop", List.of(new ProviderInfo("claude", "2.0")))
                .getFirst();

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.status()).isEqualTo(RuntimeStatus.ONLINE);
        assertThat(again.deviceName()).isEqualTo("Renamed laptop");
        assertThat(again.providerVersion()).isEqualTo("2.0");
        assertThat(again.lastHeartbeatAt()).isEqualTo(clock.instant());
        assertThat(runtimes.stored).hasSize(1);
    }

    @Test
    void theSameProviderTwiceInOneCallCountsOnce() {
        List<RuntimeView> views =
                registerAda("d1", new ProviderInfo("claude", "1.0"), new ProviderInfo("claude", "9.9"));

        assertThat(views).hasSize(1);
        assertThat(runtimes.stored).hasSize(1);
    }

    @Test
    void aComputerAnotherUserRegisteredCannotBeTakenOver() {
        registerAda("d1", new ProviderInfo("claude", "1.0"));

        assertThatThrownBy(() -> service.register(bob.id(), workspace, "d1", "Bob's laptop", List.of(new ProviderInfo("claude", "1.0"))))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("runtime.owned_by_other");

        Runtime original = runtimes.stored.values().iterator().next();
        assertThat(original.ownerUserId()).isEqualTo(ada.id());
        assertThat(original.deviceName()).isEqualTo("Ada's laptop");
    }

    @Test
    void theSameDaemonIdInAnotherWorkspaceIsAnotherRuntime() {
        UUID other = UUID.randomUUID();
        access.join(other, ada.id(), WorkspaceRole.MEMBER);
        registerAda("d1", new ProviderInfo("claude", "1.0"));

        service.register(ada.id(), other, "d1", "Ada's laptop", List.of(new ProviderInfo("claude", "1.0")));

        assertThat(runtimes.stored).hasSize(2);
    }

    @Test
    void onlyMembersCanRegister() {
        assertThatThrownBy(() -> service.register(outsider.id(), workspace, "d1", "Laptop", List.of(new ProviderInfo("claude", ""))))
                .isInstanceOf(NotFoundException.class);

        assertThat(runtimes.stored).isEmpty();
    }

    @Test
    void rejectsInvalidInputWithoutStoringAnything() {
        assertThatThrownBy(() -> registerAda("d1")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.register(ada.id(), workspace, "d1", "Laptop", null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> registerAda("d1", new ProviderInfo("Not Valid", ""))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> registerAda("  ", new ProviderInfo("claude", ""))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.register(ada.id(), workspace, "d1", " ", List.of(new ProviderInfo("claude", ""))))
                .isInstanceOf(ValidationException.class);
        List<ProviderInfo> tooMany = java.util.stream.IntStream.range(0, 21)
                .mapToObj(i -> new ProviderInfo("tool" + i, ""))
                .toList();
        assertThatThrownBy(() -> service.register(ada.id(), workspace, "d1", "Laptop", tooMany))
                .isInstanceOf(ValidationException.class);

        assertThat(runtimes.stored).isEmpty();
    }

    // ---- heartbeat ---------------------------------------------------------------------------------------

    @Test
    void aHeartbeatRefreshesTheOwnRuntimesAndKeepsThemOnline() {
        RuntimeView view = registerAda("d1", new ProviderInfo("claude", "1.0")).getFirst();
        clock.advance(Duration.ofSeconds(80));

        List<UUID> refreshed = service.heartbeat(ada.id(), List.of(view.id()));
        clock.advance(Duration.ofSeconds(80));
        int wentOffline = service.markStaleOffline();

        assertThat(refreshed).containsExactly(view.id());
        assertThat(wentOffline).as("80 s since the last heartbeat is fine").isZero();
        assertThat(stored(view).status()).isEqualTo(RuntimeStatus.ONLINE);
    }

    @Test
    void aHeartbeatBringsAnOfflineRuntimeBack() {
        RuntimeView view = registerAda("d1", new ProviderInfo("claude", "1.0")).getFirst();
        clock.advance(Duration.ofMinutes(5));
        service.markStaleOffline();

        service.heartbeat(ada.id(), List.of(view.id()));

        assertThat(stored(view).status()).isEqualTo(RuntimeStatus.ONLINE);
    }

    @Test
    void aHeartbeatNeverTouchesSomeoneElsesOrUnknownRuntimes() {
        RuntimeView adas = registerAda("d1", new ProviderInfo("claude", "1.0")).getFirst();
        clock.advance(Duration.ofSeconds(60));

        List<UUID> refreshed = service.heartbeat(bob.id(), List.of(adas.id(), UUID.randomUUID()));

        assertThat(refreshed).isEmpty();
        assertThat(stored(adas).lastHeartbeatAt()).isEqualTo(adas.lastHeartbeatAt());
    }

    // ---- deregister --------------------------------------------------------------------------------------

    @Test
    void deregisteringTakesTheOwnRuntimesOfflineAtOnce() {
        RuntimeView view = registerAda("d1", new ProviderInfo("claude", "1.0")).getFirst();

        service.deregister(ada.id(), List.of(view.id()));

        assertThat(stored(view).status()).isEqualTo(RuntimeStatus.OFFLINE);
    }

    @Test
    void deregisteringSomeoneElsesRuntimeDoesNothing() {
        RuntimeView view = registerAda("d1", new ProviderInfo("claude", "1.0")).getFirst();

        service.deregister(bob.id(), List.of(view.id()));

        assertThat(stored(view).status()).isEqualTo(RuntimeStatus.ONLINE);
    }

    // ---- the offline job ---------------------------------------------------------------------------------

    @Test
    void theJobTakesRuntimesOfflineOnlyAfterNinetySecondsOfSilence() {
        RuntimeView view = registerAda("d1", new ProviderInfo("claude", "1.0")).getFirst();

        clock.advance(Duration.ofSeconds(90));
        assertThat(service.markStaleOffline()).as("exactly 90 s").isZero();
        assertThat(stored(view).status()).isEqualTo(RuntimeStatus.ONLINE);

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.markStaleOffline()).isEqualTo(1);
        assertThat(stored(view).status()).isEqualTo(RuntimeStatus.OFFLINE);
    }

    @Test
    void theJobOnlyTouchesTheSilentOnesAndOnlyOnce() {
        RuntimeView silent = registerAda("d1", new ProviderInfo("claude", "1.0")).getFirst();
        RuntimeView alive = registerAda("d2", new ProviderInfo("claude", "1.0")).getFirst();
        clock.advance(Duration.ofSeconds(60));
        service.heartbeat(ada.id(), List.of(alive.id()));
        clock.advance(Duration.ofSeconds(60));

        assertThat(service.markStaleOffline()).isEqualTo(1);
        assertThat(service.markStaleOffline()).as("already offline").isZero();

        assertThat(stored(silent).status()).isEqualTo(RuntimeStatus.OFFLINE);
        assertThat(stored(alive).status()).isEqualTo(RuntimeStatus.ONLINE);
    }

    @Test
    void theJobCoversEveryWorkspace() {
        UUID other = UUID.randomUUID();
        access.join(other, bob.id(), WorkspaceRole.MEMBER);
        registerAda("d1", new ProviderInfo("claude", "1.0"));
        service.register(bob.id(), other, "d9", "Bob's PC", List.of(new ProviderInfo("claude", "1.0")));

        clock.advance(Duration.ofMinutes(10));

        assertThat(service.markStaleOffline()).isEqualTo(2);
    }

    // ---- list --------------------------------------------------------------------------------------------

    @Test
    void membersSeeTheRuntimesOfTheWorkspaceWithOwners() {
        registerAda("d1", new ProviderInfo("claude", "1.0"));
        service.register(bob.id(), workspace, "d2", "Bob's PC", List.of(new ProviderInfo("mock", "")));

        assertThat(service.list(bob.id(), workspace))
                .extracting(RuntimeView::ownerName, RuntimeView::deviceName)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("Ada", "Ada's laptop"),
                        org.assertj.core.api.Assertions.tuple("Bob", "Bob's PC"));
    }

    @Test
    void theListIsLimitedToTheWorkspaceAndClosedToOutsiders() {
        UUID other = UUID.randomUUID();
        access.join(other, ada.id(), WorkspaceRole.MEMBER);
        registerAda("d1", new ProviderInfo("claude", "1.0"));
        service.register(ada.id(), other, "d1", "Ada's laptop", List.of(new ProviderInfo("claude", "1.0")));

        assertThat(service.list(ada.id(), workspace)).hasSize(1);
        assertThatThrownBy(() -> service.list(outsider.id(), workspace)).isInstanceOf(NotFoundException.class);
    }
}
