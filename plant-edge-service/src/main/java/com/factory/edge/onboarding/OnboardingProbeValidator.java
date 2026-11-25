package com.factory.edge.onboarding;

import com.factory.edge.onboarding.ProbeErrorResponse.FieldErrorDetail;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The 422 gate. Every semantic check runs; {@code fieldErrors} carries every failure in endpoint,
 * mode, limit, length order, and the top-level {@code code}/{@code message} is the first of them.
 * These rules are duplicated in central-backend and kept aligned by
 * {@code contracts/onboarding-probe-validation.json}.
 */
public final class OnboardingProbeValidator {

    public static final String SECURITY_MODE_NONE = "None";
    public static final String SECURITY_MODE_SIGN_AND_ENCRYPT = "SignAndEncrypt";

    static final String ENDPOINT_URL_INVALID = "ENDPOINT_URL_INVALID";
    static final String ENDPOINT_URL_MESSAGE =
            "endpointUrl must be an opc.tcp URI with a host and a valid port";
    static final String SECURITY_MODE_UNSUPPORTED = "SECURITY_MODE_UNSUPPORTED";
    static final String SECURITY_MODE_MESSAGE = "securityMode must be None or SignAndEncrypt";
    static final String NODE_IDS_LIMIT_EXCEEDED = "NODE_IDS_LIMIT_EXCEEDED";
    static final String NODE_IDS_LIMIT_MESSAGE = "nodeIds accepts at most 32 entries";
    static final String NODE_ID_TOO_LONG = "NODE_ID_TOO_LONG";
    static final String NODE_ID_LENGTH_MESSAGE = "nodeIds entries must be at most 512 characters";

    static final int MAX_NODE_IDS = 32;
    static final int MAX_NODE_ID_LENGTH = 512;

    /** {@code error} non-null is a 422; {@code url} is present only when the URL parsed. */
    public record Validation(ProbeErrorResponse error, OnboardingProbeUrl url) {

        public boolean rejected() {
            return error != null;
        }

        public Optional<OnboardingProbeUrl> dialUrl() {
            return Optional.ofNullable(url);
        }
    }

    private OnboardingProbeValidator() {}

    public static Validation validate(OnboardingProbeRequest request) {
        String endpointUrl = request == null ? null : request.endpointUrl();
        String securityMode = request == null ? null : request.securityMode();
        List<String> nodeIds = request == null ? null : request.nodeIds();

        List<FieldErrorDetail> fieldErrors = new ArrayList<>();
        List<String> codes = new ArrayList<>();

        Optional<OnboardingProbeUrl> url = OnboardingProbeUrl.parse(endpointUrl);
        if (url.isEmpty()) {
            codes.add(ENDPOINT_URL_INVALID);
            fieldErrors.add(new FieldErrorDetail("endpointUrl", ENDPOINT_URL_MESSAGE));
        }
        if (!SECURITY_MODE_NONE.equals(securityMode)
                && !SECURITY_MODE_SIGN_AND_ENCRYPT.equals(securityMode)) {
            codes.add(SECURITY_MODE_UNSUPPORTED);
            fieldErrors.add(new FieldErrorDetail("securityMode", SECURITY_MODE_MESSAGE));
        }
        if (nodeIds != null && nodeIds.size() > MAX_NODE_IDS) {
            codes.add(NODE_IDS_LIMIT_EXCEEDED);
            fieldErrors.add(new FieldErrorDetail("nodeIds", NODE_IDS_LIMIT_MESSAGE));
        }
        if (nodeIds != null && nodeIds.stream().anyMatch(OnboardingProbeValidator::tooLong)) {
            codes.add(NODE_ID_TOO_LONG);
            fieldErrors.add(new FieldErrorDetail("nodeIds", NODE_ID_LENGTH_MESSAGE));
        }

        if (fieldErrors.isEmpty()) {
            return new Validation(null, url.orElseThrow());
        }
        return new Validation(
                new ProbeErrorResponse(
                        codes.get(0), fieldErrors.get(0).message(), List.copyOf(fieldErrors)),
                url.orElse(null));
    }

    private static boolean tooLong(String nodeId) {
        return nodeId != null && nodeId.length() > MAX_NODE_ID_LENGTH;
    }
}
