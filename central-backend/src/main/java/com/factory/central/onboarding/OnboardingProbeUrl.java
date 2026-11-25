package com.factory.central.onboarding;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * Raw-authority parse for the probe endpoint URL, duplicated from the edge on purpose: the rules,
 * codes, and messages stay in step through contracts/onboarding-probe-validation.json, not through
 * a shared jar. Central only accepts or rejects; the edge owns the canonical dial URL because
 * Central never opens the submitted endpoint. Query and fragment are rejected on both sides:
 * the edge dials {@code rawPath} only, and a strip would echo a URL that was not dialed.
 */
public final class OnboardingProbeUrl {

    static final int MAX_HOST_LENGTH = 253;

    private static final String SCHEME = "opc.tcp";
    private static final Pattern UNBRACKETED_HOST = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Pattern BRACKET_INTERIOR = Pattern.compile("[0-9A-Fa-f:.]+");
    private static final Pattern PORT_SEGMENT = Pattern.compile("[0-9]{1,5}");

    private OnboardingProbeUrl() {}

    public static boolean isValid(String endpointUrl) {
        if (endpointUrl == null || endpointUrl.isBlank()) {
            return false;
        }
        URI uri;
        try {
            // No trim: leading or trailing whitespace is a syntax failure, not a trimmed URL.
            uri = new URI(endpointUrl);
        } catch (URISyntaxException ex) {
            return false;
        }
        if (uri.getScheme() == null || !uri.getScheme().equalsIgnoreCase(SCHEME)) {
            return false;
        }
        // opc.tcp:localhost:4840 is opaque: its scheme-specific part is not a host.
        if (uri.isOpaque()) {
            return false;
        }
        String authority = uri.getRawAuthority();
        // opc.tcp:// and opc.tcp:///OPCUA/SimulationServer have no authority to dial.
        if (authority == null || authority.isEmpty()) {
            return false;
        }
        // Reject userinfo before any port scan: ":pass" in user:pass@host is not a port.
        if (uri.getRawUserInfo() != null || authority.indexOf('@') >= 0) {
            return false;
        }
        // null is absent. "" is a bare "?" or "#" and is still a component the edge would not dial.
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            return false;
        }
        return authority.charAt(0) == '[' ? isValidBracketed(authority) : isValidUnbracketed(authority);
    }

    private static boolean isValidBracketed(String authority) {
        int close = authority.indexOf(']');
        if (close < 0) {
            return false;
        }
        String interior = authority.substring(1, close);
        if (interior.isEmpty()
                || !BRACKET_INTERIOR.matcher(interior).matches()
                || interior.length() > MAX_HOST_LENGTH) {
            return false;
        }
        // Remainder after ']' is "" (omitted port, edge dials 4840) or ":" + port.
        // The port regex runs on substring(1). "[::1]4840" has no colon and is rejected.
        String remainder = authority.substring(close + 1);
        if (remainder.isEmpty()) {
            return true;
        }
        if (remainder.charAt(0) != ':') {
            return false;
        }
        return isValidPort(remainder.substring(1));
    }

    private static boolean isValidUnbracketed(String authority) {
        int colon = authority.lastIndexOf(':');
        String host = colon < 0 ? authority : authority.substring(0, colon);
        // URI.getHost() is null for an underscore host such as plc_1, so it is not the check.
        if (host.isBlank() || !UNBRACKETED_HOST.matcher(host).matches() || host.length() > MAX_HOST_LENGTH) {
            return false;
        }
        return colon < 0 || isValidPort(authority.substring(colon + 1));
    }

    /** Integer.parseInt success is not the check: it accepts +80, 0080, and non-ASCII digits. */
    private static boolean isValidPort(String portSegment) {
        if (!PORT_SEGMENT.matcher(portSegment).matches() || portSegment.charAt(0) == '0') {
            return false;
        }
        int port = Integer.parseInt(portSegment);
        return port >= 1 && port <= 65535;
    }
}
