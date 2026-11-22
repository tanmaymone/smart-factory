package com.factory.central.registry;

final class TagBoundsValidator {

    private TagBoundsValidator() {}

    static void validate(TagMappingRequest request) {
        if (request.minValue() != null
                && request.maxValue() != null
                && request.minValue() > request.maxValue()) {
            throw invalidBounds("minValue must be less than or equal to maxValue");
        }
        if (request.warningLow() != null
                && request.warningHigh() != null
                && request.warningLow() > request.warningHigh()) {
            throw invalidBounds("warningLow must be less than or equal to warningHigh");
        }
        if (request.criticalLow() != null
                && request.criticalHigh() != null
                && request.criticalLow() > request.criticalHigh()) {
            throw invalidBounds("criticalLow must be less than or equal to criticalHigh");
        }
        if (request.criticalLow() != null
                && request.warningLow() != null
                && request.criticalLow() > request.warningLow()) {
            throw invalidBounds("criticalLow must be less than or equal to warningLow");
        }
        if (request.warningHigh() != null
                && request.criticalHigh() != null
                && request.warningHigh() > request.criticalHigh()) {
            throw invalidBounds("warningHigh must be less than or equal to criticalHigh");
        }
    }

    private static RegistryException invalidBounds(String message) {
        return new RegistryException(RegistryErrorCode.INVALID_BOUNDS, message, org.springframework.http.HttpStatus.BAD_REQUEST);
    }
}
