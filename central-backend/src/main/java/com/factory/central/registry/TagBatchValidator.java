package com.factory.central.registry;

import java.util.ArrayList;
import java.util.List;

final class TagBatchValidator {

    private TagBatchValidator() {}

    record ParsedRow(
            String siteId,
            String lineId,
            String tagCode,
            String description,
            Protocol protocol,
            String endpointUrl,
            String externalAddress,
            TagDataType dataType,
            String unit,
            boolean writable,
            Double minValue,
            Double maxValue,
            double deadband,
            Double warningLow,
            Double warningHigh,
            Double criticalLow,
            Double criticalHigh,
            String interlockRuleKey,
            RegistryLifecycle lifecycle,
            boolean lifecycleExplicit) {}

    static List<TagBatchRowError> validate(TagBatchRowInput input, ParsedRowHolder holder) {
        List<TagBatchRowError> errors = new ArrayList<>();
        if (input.columnCountExceeded()) {
            errors.add(new TagBatchRowError(
                    "_columns", RegistryErrorCode.VALIDATION_FAILED, "column count exceeds header"));
        }

        String siteId = TagBatchFieldParsers.required(input, "siteId", errors);
        String lineId = TagBatchFieldParsers.required(input, "lineId", errors);
        String tagCode = TagBatchFieldParsers.required(input, "tagCode", errors);
        String description = TagBatchFieldParsers.required(input, "description", errors);
        String endpointUrl = TagBatchFieldParsers.required(input, "endpointUrl", errors);
        String externalAddress = TagBatchFieldParsers.required(input, "externalAddress", errors);
        if (externalAddress != null && externalAddress.isBlank()) {
            errors.add(new TagBatchRowError(
                    "externalAddress", RegistryErrorCode.VALIDATION_FAILED, "must not be blank"));
            externalAddress = null;
        }

        Protocol protocol = TagBatchFieldParsers.parseProtocol(input.get("protocol"), errors);
        TagDataType dataType = TagBatchFieldParsers.parseDataType(input.get("dataType"), errors);
        Boolean writable = TagBatchFieldParsers.parseBoolean(input.get("writable"), "writable", errors);
        Double deadband = TagBatchFieldParsers.parseDouble(input.get("deadband"), "deadband", true, errors);
        if (deadband != null && deadband < 0) {
            errors.add(new TagBatchRowError("deadband", RegistryErrorCode.VALIDATION_FAILED, "must be >= 0"));
            deadband = null;
        }

        Double minValue = TagBatchFieldParsers.parseDouble(input.get("minValue"), "minValue", false, errors);
        Double maxValue = TagBatchFieldParsers.parseDouble(input.get("maxValue"), "maxValue", false, errors);
        Double warningLow = TagBatchFieldParsers.parseDouble(input.get("warningLow"), "warningLow", false, errors);
        Double warningHigh =
                TagBatchFieldParsers.parseDouble(input.get("warningHigh"), "warningHigh", false, errors);
        Double criticalLow =
                TagBatchFieldParsers.parseDouble(input.get("criticalLow"), "criticalLow", false, errors);
        Double criticalHigh =
                TagBatchFieldParsers.parseDouble(input.get("criticalHigh"), "criticalHigh", false, errors);

        if (minValue != null && maxValue != null && minValue >= maxValue) {
            errors.add(new TagBatchRowError(
                    "minValue", RegistryErrorCode.INVALID_BOUNDS, "minValue must be less than maxValue"));
        }

        TagMappingRequest boundsProbe = new TagMappingRequest(
                siteId == null ? "x" : siteId,
                lineId == null ? "x" : lineId,
                tagCode == null ? "x" : tagCode,
                description == null ? "x" : description,
                protocol == null ? Protocol.OPC_UA : protocol,
                endpointUrl == null ? "x" : endpointUrl,
                externalAddress == null ? "x" : externalAddress,
                dataType == null ? TagDataType.FLOAT : dataType,
                TagBatchFieldParsers.blankToNull(input.get("unit")),
                writable == null ? Boolean.FALSE : writable,
                minValue,
                maxValue,
                deadband == null ? 0.0 : deadband,
                warningLow,
                warningHigh,
                criticalLow,
                criticalHigh,
                TagBatchFieldParsers.blankToNull(input.get("interlockRuleKey")),
                null);
        for (RegistryErrorResponse.FieldErrorDetail detail : TagBoundsValidator.collect(boundsProbe)) {
            if ("minValue".equals(detail.field()) && minValue != null && maxValue != null && minValue >= maxValue) {
                continue;
            }
            errors.add(new TagBatchRowError(detail.field(), RegistryErrorCode.INVALID_BOUNDS, detail.message()));
        }

        String lifecycleRaw = input.get("lifecycle");
        boolean lifecycleExplicit = lifecycleRaw != null && !lifecycleRaw.isBlank();
        RegistryLifecycle lifecycle = null;
        if (lifecycleExplicit) {
            lifecycle = TagBatchFieldParsers.parseLifecycle(lifecycleRaw, errors);
        }

        if (errors.isEmpty()
                && siteId != null
                && lineId != null
                && tagCode != null
                && description != null
                && protocol != null
                && endpointUrl != null
                && externalAddress != null
                && dataType != null
                && writable != null
                && deadband != null) {
            holder.value = new ParsedRow(
                    siteId,
                    lineId,
                    tagCode,
                    description,
                    protocol,
                    endpointUrl,
                    externalAddress,
                    dataType,
                    TagBatchFieldParsers.blankToNull(input.get("unit")),
                    writable,
                    minValue,
                    maxValue,
                    deadband,
                    warningLow,
                    warningHigh,
                    criticalLow,
                    criticalHigh,
                    TagBatchFieldParsers.blankToNull(input.get("interlockRuleKey")),
                    lifecycle,
                    lifecycleExplicit);
        }
        return errors;
    }

    static final class ParsedRowHolder {
        ParsedRow value;
    }
}
