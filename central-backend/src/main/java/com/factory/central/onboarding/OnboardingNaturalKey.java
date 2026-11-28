package com.factory.central.onboarding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class OnboardingNaturalKey {

    static final Pattern SITE_OR_LINE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");

    private OnboardingNaturalKey() {}

    static void requireSiteId(String raw) {
        if (raw == null || raw.isBlank() || !SITE_OR_LINE.matcher(raw.trim()).matches()) {
            throw invalid("siteId", "must match ^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");
        }
    }

    static void requireLineId(String raw) {
        if (raw == null || raw.isBlank() || !SITE_OR_LINE.matcher(raw.trim()).matches()) {
            throw invalid("lineId", "must match ^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");
        }
    }

    static void requireEndpointUrl(String raw) {
        if (raw == null || raw.isBlank() || !OnboardingProbeUrl.isValid(raw)) {
            throw invalid("endpointUrl", "endpointUrl must be an opc.tcp URI with a host and a valid port");
        }
    }

    private static OnboardingCatalogException invalid(String field, String message) {
        return new OnboardingCatalogException(
                OnboardingErrorCode.INVALID_PARAMETER,
                "Invalid value for parameter '" + field + "'",
                org.springframework.http.HttpStatus.BAD_REQUEST,
                List.of(new OnboardingErrorResponse.FieldErrorDetail(field, message)));
    }

    static List<OnboardingErrorResponse.FieldErrorDetail> invalidFormat() {
        return List.of(new OnboardingErrorResponse.FieldErrorDetail("format", "format must be csv or json"));
    }
}