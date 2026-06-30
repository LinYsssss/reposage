package com.example.reposage.sandbox;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * SSRF guard: only public https endpoints pass; loopback, private, link-local/metadata, and non-https
 * URLs are rejected. Tests use literal IPs so no DNS lookup is needed.
 */
class RemoteResourceGuardTest {

    private final RemoteResourceGuard guard = new RemoteResourceGuard();

    @Test
    void allowsPublicHttpsAddress() {
        assertThatCode(() -> guard.requireAllowed("https://8.8.8.8/repo.git")).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonHttps() {
        assertThatThrownBy(() -> guard.requireAllowed("http://8.8.8.8/repo.git"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsLoopback() {
        assertThatThrownBy(() -> guard.requireAllowed("https://127.0.0.1/x"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsPrivateSiteLocal() {
        assertThatThrownBy(() -> guard.requireAllowed("https://10.0.0.1/x"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> guard.requireAllowed("https://192.168.1.5/x"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsCloudMetadataLinkLocal() {
        assertThatThrownBy(() -> guard.requireAllowedSubmodule("https://169.254.169.254/latest/meta-data"))
                .isInstanceOf(SecurityException.class);
    }
}
