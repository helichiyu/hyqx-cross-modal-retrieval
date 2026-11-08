package com.hsmty.cmr.common.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionsTest {

    @Test
    void adminHasAllPermissions() {
        assertThat(Permissions.of(Permissions.ROLE_ADMIN)).containsAll(Permissions.ALL);
    }

    @Test
    void operatorCanManageOperationsButNotDatasetCreation() {
        assertThat(Permissions.of(Permissions.ROLE_OPERATOR))
                .contains("cmr:strategy:publish", "cmr:eval:run", "cmr:feedback:update")
                .doesNotContain("cmr:dataset:create");
    }

    @Test
    void userCanSearchAndReadTasksOnly() {
        assertThat(Permissions.of(Permissions.ROLE_USER))
                .contains("cmr:search:create", "cmr:search:page", "cmr:task:page", "cmr:file:read")
                .doesNotContain("cmr:strategy:publish", "cmr:eval:run");
    }

    @Test
    void unknownRoleFallsBackToUserPermissions() {
        assertThat(Permissions.of("unknown")).containsExactlyElementsOf(Permissions.of(Permissions.ROLE_USER));
    }
}