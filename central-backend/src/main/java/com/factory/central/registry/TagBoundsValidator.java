package com.factory.central.registry;

import java.util.ArrayList;
import java.util.List;

final class TagBoundsValidator {

    private TagBoundsValidator() {}

    static void validate(TagMappingRequest request) {
        List<RegistryErrorResponse.FieldErrorDetail> errors = collect(request);
        if (!errors.isEmpty()) {
            throw new RegistryException(
                    RegistryErrorCode.INVALID_BOUNDS,
                    errors.getFirst().message(),
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }

    static List<RegistryErrorResponse.FieldErrorDetail> collect(TagMappingRequest request) {
        List<RegistryErrorResponse.FieldErrorDetail> errors = new ArrayList<>();
        if (request.minValue() != null
                && request.maxValue() != null
                && request.minValue() > request.maxValue()) {
            errors.add(new RegistryErrorResponse.FieldErrorDetail(
                    "minValue", "minValue must be less than or equal to maxValue"));
        }
        if (request.warningLow() != null
                && request.warningHigh() != null
                && request.warningLow() > request.warningHigh()) {
            errors.add(new RegistryErrorResponse.FieldErrorDetail(
                    "warningLow", "warningLow must be less than or equal to warningHigh"));
        }
        if (request.criticalLow() != null
                && request.criticalHigh() != null
                && request.criticalLow() > request.criticalHigh()) {
            errors.add(new RegistryErrorResponse.FieldErrorDetail(
                    "criticalLow", "criticalLow must be less than or equal to criticalHigh"));
        }
        if (request.criticalLow() != null
                && request.warningLow() != null
                && request.criticalLow() > request.warningLow()) {
            errors.add(new RegistryErrorResponse.FieldErrorDetail(
                    "criticalLow", "criticalLow must be less than or equal to warningLow"));
        }
        if (request.warningHigh() != null
                && request.criticalHigh() != null
                && request.warningHigh() > request.criticalHigh()) {
            errors.add(new RegistryErrorResponse.FieldErrorDetail(
                    "warningHigh", "warningHigh must be less than or equal to criticalHigh"));
        }
        return errors;
    }
}
