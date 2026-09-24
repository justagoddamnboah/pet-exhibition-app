package edu.rutmiit.enterprise.exhibition.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SessionSecurityConfigTest {
    private final SessionSecurityConfig configuration = new SessionSecurityConfig();

    @Test
    void passwordsAreHashedAndAdminGetsBothRoles() {
        var encoder = configuration.passwordEncoder();
        var users = configuration.users(encoder);
        var admin = users.loadUserByUsername("admin");

        assertThat(admin.getPassword()).isNotEqualTo("admin");
        assertThat(encoder.matches("admin", admin.getPassword())).isTrue();
        assertThat(AuthorityUtils.authorityListToSet(admin.getAuthorities()))
                .containsExactlyInAnyOrder("ROLE_VISITOR", "ROLE_ADMIN");
    }
}