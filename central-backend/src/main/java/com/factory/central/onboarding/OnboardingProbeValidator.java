package com.factory.central.onboarding;

import com.factory.central.onboarding.ProbeErrorResponse.FieldErrorDetail;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Semantic 422 gate. Duplicated from the edge: same codes, same messages, same fieldErrors order,
 * kept in step by contracts/onboarding-probe-validation.json.
 */
public final class OnboardingProbeValidator {

    public static final String ENDPOINT_URL_INVALID = "ENDPOINT_URL_INVALID";
    public static final String SECURITY_MODE_UNSUPPORTED = "SECURITY_MODE_UNSUPPORTED";
    public static final String NODE_IDS_LIMIT_EXCEEDED = "NODE_IDS_LIMIT_EXCEEDED";
    public static final String NODE_ID_TOO_LONG = "NODE_ID_TOO_LONG";

    static final String ENDPOINT_URL_MESSAGE = "endpointUrl must be an opc.tcp URI with a host and a valid port";
    static final String SECURITY_MODE_MESSAGE = "securityMode must be None or SignAndEncrypt";
    static final String NODE_IDS_LIMIT_MESSAGE = "nodeIds accepts at most 32 entries";
    static final String NODE_ID_LENGTH_MESSAGE = "nodeIds entries must be at most 512 characters";

    static final int MAX_NODE_IDS = 32;
    static final int MAX_NODE_ID_LENGTH = 512;

    private OnboardingProbeValidator() {}

    /** Empty when the request is semantically valid, otherwise the whole 422 body. */
    public static Optional<ProbeErrorResponse> validate(OnboardingProbeRequest request) {
        List<FieldErrorDetail> fieldErrors = new ArrayList<>(4);
        List<String> codes = new ArrayList<>(4);

        if (request == null || !OnboardingProbeUrl.isValid(request.endpointUrl())) {
            codes.add(ENDPOINT_URL_INVALID);
            fieldErrors.add(new FieldErrorDetail("endpointUrl", ENDPOINT_URL_MESSAGE));
        }
        String securityMode = request == null ? null : request.securityMode();
        if (!"None".equals(securityMode) && !"SignAndEncrypt".equals(securityMode)) {
            codes.add(SECURITY_MODE_UNSUPPORTED);
            fieldErrors.add(new FieldErrorDetail("securityMode", SECURITY_MODE_MESSAGE));
        }
        List<String> nodeIds = request == null ? null : request.nodeIds();
        if (nodeIds != null) {
            if (nodeIds.size() > MAX_NODE_IDS) {
                codes.add(NODE_IDS_LIMIT_EXCEEDED);
                fieldErrors.add(new FieldErrorDetail("nodeIds", NODE_IDS_LIMIT_MESSAGE));
            }
            boolean tooLong = nodeIds.stream().anyMatch(id -> id != null && id.length() > MAX_NODE_ID_LENGTH);
            if (tooLong) {
                codes.add(NODE_ID_TOO_LONG);
                fieldErrors.add(new FieldErrorDetail("nodeIds", NODE_ID_LENGTH_MESSAGE));
            }
        }

        if (fieldErrors.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ProbeErrorResponse(
                codes.get(0), fieldErrors.get(0).message(), List.copyOf(fieldErrors)));
    }
}
