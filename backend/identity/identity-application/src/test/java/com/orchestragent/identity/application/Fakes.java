package com.orchestragent.identity.application;

import com.orchestragent.identity.application.port.AccessTokenIssuer;
import com.orchestragent.identity.application.port.CodeHasher;
import com.orchestragent.identity.application.port.DevLoginCodeProvider;
import com.orchestragent.identity.application.port.LoginCodeGenerator;
import com.orchestragent.identity.application.port.LoginCodeSender;
import com.orchestragent.identity.domain.ApiToken;
import com.orchestragent.identity.domain.ApiTokenId;
import com.orchestragent.identity.domain.ApiTokenRepository;
import com.orchestragent.identity.domain.Email;
import com.orchestragent.identity.domain.LoginCode;
import com.orchestragent.identity.domain.LoginCodeRepository;
import com.orchestragent.identity.domain.User;
import com.orchestragent.identity.domain.UserId;
import com.orchestragent.identity.domain.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory implementations of the ports, so use cases are tested without Spring or a database. */
final class Fakes {

    private Fakes() {
    }

    static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static final class InMemoryLoginCodes implements LoginCodeRepository {

        final List<LoginCode> stored = new ArrayList<>();

        @Override
        public void save(LoginCode code) {
            if (!stored.contains(code)) {
                stored.add(code);
            }
        }

        @Override
        public Optional<Instant> findLatestCreatedAt(Email email) {
            return latest(email).map(LoginCode::createdAt);
        }

        @Override
        public Optional<LoginCode> findLatestForUpdate(Email email) {
            return latest(email);
        }

        private Optional<LoginCode> latest(Email email) {
            return stored.stream()
                    .filter(code -> code.email().equals(email))
                    .max(Comparator.comparing(LoginCode::createdAt));
        }
    }

    static final class InMemoryUsers implements UserRepository {

        final Map<Email, User> byEmail = new HashMap<>();

        @Override
        public Optional<User> findById(UserId id) {
            return byEmail.values().stream().filter(user -> user.id().equals(id)).findFirst();
        }

        @Override
        public Optional<User> findByEmail(Email email) {
            return Optional.ofNullable(byEmail.get(email));
        }

        @Override
        public User createIfAbsent(User user) {
            return byEmail.computeIfAbsent(user.email(), email -> user);
        }
    }

    static final class InMemoryApiTokens implements ApiTokenRepository {

        final Map<ApiTokenId, ApiToken> byId = new HashMap<>();

        @Override
        public void save(ApiToken token) {
            byId.put(token.id(), token);
        }

        @Override
        public Optional<ApiToken> findById(ApiTokenId id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<ApiToken> findByHash(String tokenHash) {
            return byId.values().stream().filter(token -> token.tokenHash().equals(tokenHash)).findFirst();
        }

        @Override
        public List<ApiToken> findByUser(UserId userId) {
            return byId.values().stream()
                    .filter(token -> token.userId().equals(userId))
                    .sorted(Comparator.comparing(ApiToken::createdAt).reversed())
                    .toList();
        }
    }

    static final class FixedCodeGenerator implements LoginCodeGenerator {

        String next = "123456";

        @Override
        public String generate() {
            return next;
        }
    }

    static final class FakeHasher implements CodeHasher {

        @Override
        public String hash(Email email, String code) {
            return "hash:" + email.value() + ":" + code;
        }

        @Override
        public boolean matches(Email email, String code, String storedHash) {
            return hash(email, code).equals(storedHash);
        }
    }

    static final class CapturingSender implements LoginCodeSender {

        final List<String> codes = new ArrayList<>();
        final List<Email> recipients = new ArrayList<>();

        @Override
        public void send(Email to, String code, Instant expiresAt) {
            recipients.add(to);
            codes.add(code);
        }
    }

    static final class FakeIssuer implements AccessTokenIssuer {

        @Override
        public IssuedAccessToken issue(User user) {
            return new IssuedAccessToken("jwt-for-" + user.id().value(), Instant.parse("2026-01-08T10:00:00Z"));
        }
    }

    static final class FakeDevCode implements DevLoginCodeProvider {

        Optional<String> code = Optional.empty();

        @Override
        public Optional<String> code() {
            return code;
        }
    }
}
