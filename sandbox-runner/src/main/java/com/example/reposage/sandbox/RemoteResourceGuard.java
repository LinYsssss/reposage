package com.example.reposage.sandbox;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Guards outbound fetches (repository archives, submodule URLs) against SSRF.
 *
 * <p>Only {@code https} is allowed, and the resolved address must be a public unicast address —
 * loopback, link-local (including the {@code 169.254.169.254} cloud metadata endpoint), site-local
 * (private RFC1918), wildcard, and multicast addresses are all rejected. Submodule URLs get the same
 * treatment so a malicious {@code .gitmodules} cannot pivot to internal services.
 */
public class RemoteResourceGuard {

    private static final String METADATA_ADDRESS = "169.254.169.254";

    /** Throws if {@code url} is not a safe, public https endpoint. */
    public void requireAllowed(String url) {
        URI uri = parse(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new SecurityException("only https is allowed: " + url);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SecurityException("missing host: " + url);
        }
        InetAddress address = resolve(host);
        if (address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress()
                || METADATA_ADDRESS.equals(address.getHostAddress())) {
            throw new SecurityException("host resolves to a non-public address: " + host);
        }
    }

    /** Submodule URLs face the same restrictions as any other outbound fetch. */
    public void requireAllowedSubmodule(String url) {
        requireAllowed(url);
    }

    private static URI parse(String url) {
        try {
            return new URI(url);
        } catch (Exception ex) {
            throw new SecurityException("invalid URL: " + url);
        }
    }

    private static InetAddress resolve(String host) {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException ex) {
            throw new SecurityException("cannot resolve host: " + host);
        }
    }
}
