package com.factory.edge.onboarding;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Parsed probe endpoint. The host comes from {@link URI#getRawAuthority()} — {@code URI.getHost()}
 * is null for plant names such as {@code plc_1}, and a scan of the raw authority for the first
 * {@code :} would read {@code :pass} out of userinfo as a port.
 *
 * @param host host text exactly as submitted, IPv6 brackets kept
 * @param port submitted port, or 4840 when the authority had no port segment
 * @param dialUrl canonical {@code opc.tcp://host:port + rawPath} used for discovery and the rewrite.
 *     Query and fragment are rejected, not dropped from this string.
 */
public record OnboardingProbeUrl(String host, int port, String dialUrl) {

    private static final int DEFAULT_PORT = 4840;
    private static final int MAX_HOST_LENGTH = 253;
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Pattern IPV6_INTERIOR = Pattern.compile("[0-9A-Fa-f:.]+");
    private static final Pattern PORT = Pattern.compile("[0-9]{1,5}");

    /** Empty means {@code ENDPOINT_URL_INVALID}. No trimming: whitespace is a rejection. */
    public static Optional<OnboardingProbeUrl> parse(String endpointUrl) {
        if (endpointUrl == null || endpointUrl.isEmpty()) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(endpointUrl);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        if (!"opc.tcp".equalsIgnoreCase(uri.getScheme())) {
            return Optional.empty();
        }
        // opc.tcp:localhost:4840 has no "//": never parse the scheme-specific part as a host.
        if (uri.isOpaque()) {
            return Optional.empty();
        }
        String authority = uri.getRawAuthority();
        if (authority == null || authority.isEmpty()) {
            return Optional.empty();
        }
        if (uri.getRawUserInfo() != null || authority.indexOf('@') >= 0) {
            return Optional.empty();
        }
        // opc.tcp is host[:port][/path]. Query and fragment are not dialed: build() copies
        // rawPath only, and the response echoes the submitted URL. null is absent; "" is a
        // bare "?" or "#" and is still present.
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            return Optional.empty();
        }
        return authority.startsWith("[")
                ? bracketed(uri, authority)
                : unbracketed(uri, authority);
    }

    private static Optional<OnboardingProbeUrl> bracketed(URI uri, String authority) {
        int close = authority.indexOf(']');
        if (close < 0) {
            return Optional.empty();
        }
        String interior = authority.substring(1, close);
        if (interior.isEmpty()
                || interior.length() > MAX_HOST_LENGTH
                || !IPV6_INTERIOR.matcher(interior).matches()) {
            return Optional.empty();
        }
        // Remainder after ']' is "" (omitted port → 4840) or ":" + port.
        // The port regex runs on substring(1). "[::1]4840" has no colon and is rejected.
        String remainder = authority.substring(close + 1);
        if (!remainder.isEmpty() && remainder.charAt(0) != ':') {
            return Optional.empty();
        }
        String portSegment = remainder.isEmpty() ? null : remainder.substring(1);
        return build(uri, authority.substring(0, close + 1), portSegment);
    }

    private static Optional<OnboardingProbeUrl> unbracketed(URI uri, String authority) {
        int colon = authority.lastIndexOf(':');
        String host = colon < 0 ? authority : authority.substring(0, colon);
        String portSegment = colon < 0 ? null : authority.substring(colon + 1);
        // HOST excludes ':', so a second colon outside brackets fails here.
        if (host.isEmpty() || host.length() > MAX_HOST_LENGTH || !HOST.matcher(host).matches()) {
            return Optional.empty();
        }
        return build(uri, host, portSegment);
    }

    private static Optional<OnboardingProbeUrl> build(URI uri, String host, String portSegment) {
        int port = DEFAULT_PORT;
        if (portSegment != null) {
            // Integer.parseInt is not the check: it accepts +80, 0080, and non-ASCII digits.
            if (!PORT.matcher(portSegment).matches() || portSegment.charAt(0) == '0') {
                return Optional.empty();
            }
            port = Integer.parseInt(portSegment);
            if (port < 1 || port > 65535) {
                return Optional.empty();
            }
        }
        String rawPath = uri.getRawPath();
        // Never String.valueOf(rawPath): that writes the characters "null" onto the dial URL.
        String path = rawPath == null ? "" : rawPath;
        String dialUrl = "opc.tcp://" + host + ":" + port + path;
        // Scheme case and an omitted port are the only legal differences. A component that
        // did not land in dialUrl (query, fragment, userinfo, or a dropped path) is a rejection.
        if (!retainedOnDial(uri, host, port, dialUrl)) {
            return Optional.empty();
        }
        return Optional.of(new OnboardingProbeUrl(host, port, dialUrl));
    }

    private static boolean retainedOnDial(URI submitted, String host, int port, String dialUrl) {
        if (submitted.getRawUserInfo() != null
                || submitted.getRawQuery() != null
                || submitted.getRawFragment() != null) {
            return false;
        }
        URI dial;
        try {
            dial = new URI(dialUrl);
        } catch (URISyntaxException e) {
            return false;
        }
        String submittedPath = submitted.getRawPath() == null ? "" : submitted.getRawPath();
        String dialPath = dial.getRawPath() == null ? "" : dial.getRawPath();
        return submittedPath.equals(dialPath)
                && dial.getRawQuery() == null
                && dial.getRawFragment() == null
                && dial.getRawUserInfo() == null
                && (host + ":" + port).equals(dial.getRawAuthority());
    }
}
