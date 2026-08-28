package com.mypetadmin.ps_gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VersionControllerTest {

    @Test
    void retornaVersaoECommitConfigurados() {
        VersionController controller = new VersionController("1.2.3", "abc123");

        VersionController.VersionResponse response = controller.version();

        assertThat(response.service()).isEqualTo("ps-gateway");
        assertThat(response.version()).isEqualTo("1.2.3");
        assertThat(response.commit()).isEqualTo("abc123");
    }
}
