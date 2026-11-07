package com.orchestragent.agent.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The whole truth table, written out by hand (the web app has the same table in permissions.ts). */
class AgentPolicyTest {

    @Test
    void theCreatorAndAdminsAndOwnersMayManageAnAgent() {
        assertThat(AgentPolicy.canManage(true, true)).as("creator and admin").isTrue();
        assertThat(AgentPolicy.canManage(true, false)).as("creator, plain member").isTrue();
        assertThat(AgentPolicy.canManage(false, true)).as("someone else's agent, admin or owner").isTrue();
        assertThat(AgentPolicy.canManage(false, false)).as("someone else's agent, plain member").isFalse();
    }
}
