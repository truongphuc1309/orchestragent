package com.orchestragent.workspace.domain;

import static com.orchestragent.workspace.domain.Role.ADMIN;
import static com.orchestragent.workspace.domain.Role.MEMBER;
import static com.orchestragent.workspace.domain.Role.OWNER;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The expected answers are written out by hand, so a mistake in the policy cannot hide behind itself. */
class MemberPolicyTest {

    @ParameterizedTest(name = "{0} inviting as {1} -> {2}")
    @CsvSource({
        "OWNER,  OWNER,  true",
        "OWNER,  ADMIN,  true",
        "OWNER,  MEMBER, true",
        "ADMIN,  OWNER,  false",
        "ADMIN,  ADMIN,  true",
        "ADMIN,  MEMBER, true",
        "MEMBER, OWNER,  false",
        "MEMBER, ADMIN,  false",
        "MEMBER, MEMBER, false",
    })
    void invite(Role actor, Role invitedRole, boolean allowed) {
        assertThat(MemberPolicy.canInvite(actor, invitedRole)).isEqualTo(allowed);
    }

    @ParameterizedTest(name = "{0} changing {1} to {2} -> {3}")
    @CsvSource({
        "OWNER,  OWNER,  OWNER,  true",
        "OWNER,  OWNER,  ADMIN,  true",
        "OWNER,  OWNER,  MEMBER, true",
        "OWNER,  ADMIN,  OWNER,  true",
        "OWNER,  ADMIN,  ADMIN,  true",
        "OWNER,  ADMIN,  MEMBER, true",
        "OWNER,  MEMBER, OWNER,  true",
        "OWNER,  MEMBER, ADMIN,  true",
        "OWNER,  MEMBER, MEMBER, true",
        "ADMIN,  OWNER,  OWNER,  false",
        "ADMIN,  OWNER,  ADMIN,  false",
        "ADMIN,  OWNER,  MEMBER, false",
        "ADMIN,  ADMIN,  OWNER,  false",
        "ADMIN,  ADMIN,  ADMIN,  false",
        "ADMIN,  ADMIN,  MEMBER, false",
        "ADMIN,  MEMBER, OWNER,  false",
        "ADMIN,  MEMBER, ADMIN,  true",
        "ADMIN,  MEMBER, MEMBER, true",
        "MEMBER, OWNER,  MEMBER, false",
        "MEMBER, ADMIN,  MEMBER, false",
        "MEMBER, MEMBER, ADMIN,  false",
        "MEMBER, MEMBER, OWNER,  false",
    })
    void changeRole(Role actor, Role targetCurrent, Role newRole, boolean allowed) {
        assertThat(MemberPolicy.canChangeRole(actor, targetCurrent, newRole)).isEqualTo(allowed);
    }

    @ParameterizedTest(name = "{0} removing {1} -> {2}")
    @CsvSource({
        "OWNER,  OWNER,  true",
        "OWNER,  ADMIN,  true",
        "OWNER,  MEMBER, true",
        "ADMIN,  OWNER,  false",
        "ADMIN,  ADMIN,  false",
        "ADMIN,  MEMBER, true",
        "MEMBER, OWNER,  false",
        "MEMBER, ADMIN,  false",
        "MEMBER, MEMBER, false",
    })
    void removeSomeoneElse(Role actor, Role target, boolean allowed) {
        assertThat(MemberPolicy.canRemove(actor, target, false)).isEqualTo(allowed);
    }

    @Test
    void everyoneMayRemoveThemselves() {
        for (Role role : Role.values()) {
            assertThat(MemberPolicy.canRemove(role, role, true)).as("%s leaving", role).isTrue();
        }
    }

    @Test
    void onlyAdminsAndOwnersManageInvitations() {
        assertThat(MemberPolicy.canManageInvitations(OWNER)).isTrue();
        assertThat(MemberPolicy.canManageInvitations(ADMIN)).isTrue();
        assertThat(MemberPolicy.canManageInvitations(MEMBER)).isFalse();
    }
}
