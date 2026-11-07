package com.orchestragent.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.identity.application.ApiTokenService.CreatedApiToken;
import com.orchestragent.identity.application.Fakes.InMemoryApiTokens;
import com.orchestragent.identity.application.Fakes.MutableClock;
import com.orchestragent.identity.contract.VerifiedApiToken;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.shared.error.ValidationException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiTokenServiceTest {

    private final InMemoryApiTokens repository = new InMemoryApiTokens();
    private final MutableClock clock = new MutableClock();
    private final ApiTokenService service = new ApiTokenService(repository, clock);
    private final ApiTokenVerifierService verifier = new ApiTokenVerifierService(repository, clock);
    private final UUID owner = UUID.randomUUID();

    @Test
    void createdTokenHasOatPrefixAndOnlyItsHashIsStored() {
        CreatedApiToken created = service.create(owner, "  laptop ");

        assertThat(created.rawToken()).startsWith("oat_").hasSizeGreaterThan(40);
        assertThat(created.info().name()).isEqualTo("laptop");
        assertThat(created.info().tokenPrefix()).isEqualTo(created.rawToken().substring(0, 10));
        assertThat(repository.byId.values()).singleElement().satisfies(stored -> {
            assertThat(stored.tokenHash()).isEqualTo(ApiTokens.hash(created.rawToken()));
            assertThat(stored.tokenHash()).doesNotContain(created.rawToken());
        });
    }

    @Test
    void tokensAreUnique() {
        assertThat(service.create(owner, "a").rawToken()).isNotEqualTo(service.create(owner, "b").rawToken());
    }

    @Test
    void rejectsBlankOrTooLongNames() {
        assertThatThrownBy(() -> service.create(owner, "   ")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create(owner, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create(owner, "x".repeat(101))).isInstanceOf(ValidationException.class);
    }

    @Test
    void listShowsOnlyTheCallersTokensNewestFirst() {
        service.create(owner, "first");
        clock.advance(Duration.ofSeconds(1));
        service.create(owner, "second");
        service.create(UUID.randomUUID(), "someone else's");

        assertThat(service.list(owner)).extracting(ApiTokenService.ApiTokenInfo::name).containsExactly("second", "first");
    }

    @Test
    void verifierAcceptsAValidTokenAndRecordsUse() {
        CreatedApiToken created = service.create(owner, "laptop");

        Optional<VerifiedApiToken> verified = verifier.verify(created.rawToken());

        assertThat(verified).isPresent();
        assertThat(verified.get().userId()).isEqualTo(owner);
        assertThat(verified.get().tokenId()).isEqualTo(created.info().id());
        assertThat(service.list(owner).get(0).lastUsedAt()).isEqualTo(clock.instant());
    }

    @Test
    void verifierRejectsUnknownMalformedAndEmptyTokens() {
        service.create(owner, "laptop");

        assertThat(verifier.verify("oat_unknown")).isEmpty();
        assertThat(verifier.verify("not-an-oat-token")).isEmpty();
        assertThat(verifier.verify("oat_")).isEmpty();
        assertThat(verifier.verify(null)).isEmpty();
    }

    @Test
    void revokedTokenIsRejected() {
        CreatedApiToken created = service.create(owner, "laptop");

        service.revoke(owner, created.info().id());

        assertThat(verifier.verify(created.rawToken())).isEmpty();
        assertThat(service.list(owner).get(0).revokedAt()).isNotNull();
    }

    @Test
    void cannotRevokeSomeoneElsesToken() {
        CreatedApiToken created = service.create(owner, "laptop");

        assertThatThrownBy(() -> service.revoke(UUID.randomUUID(), created.info().id()))
                .isInstanceOf(NotFoundException.class);
        assertThat(verifier.verify(created.rawToken())).isPresent();
    }

    @Test
    void revokingAnUnknownTokenIsNotFound() {
        assertThatThrownBy(() -> service.revoke(owner, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }
}
