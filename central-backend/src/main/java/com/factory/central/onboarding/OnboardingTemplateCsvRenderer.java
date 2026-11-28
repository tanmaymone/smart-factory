package com.factory.central.onboarding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class OnboardingTemplateCsvRenderer {

    private static final String HEADER =
            "siteId,lineId,tagCode,description,protocol,endpointUrl,externalAddress,dataType,unit,writable,minValue,maxValue,deadband,warningLow,warningHigh,criticalLow,criticalHigh,interlockRuleKey,lifecycle";

    private OnboardingTemplateCsvRenderer() {}

    static String render(List<EquipmentTemplateDtos.EquipmentTemplateTagRow> rows) {
        StringBuilder out = new StringBuilder(HEADER);
        out.append("\r\n");
        for (EquipmentTemplateDtos.EquipmentTemplateTagRow row : rows) {
            out.append(joinRow(row)).append("\r\n");
        }
        return out.toString();
    }

    private static String joinRow(EquipmentTemplateDtos.EquipmentTemplateTagRow row) {
        List<String> cells = new ArrayList<>(19);
        cells.add(row.siteId());
        cells.add(row.lineId());
        cells.add(row.tagCode());
        cells.add(row.description());
        cells.add(row.protocol());
        cells.add(row.endpointUrl());
        cells.add(row.externalAddress());
        cells.add(row.dataType());
        cells.add(row.unit() == null ? "" : row.unit());
        cells.add(Boolean.toString(row.writable()));
        cells.add(formatDouble(row.minValue()));
        cells.add(formatDouble(row.maxValue()));
        cells.add(formatDouble(row.deadband()));
        cells.add(formatDouble(row.warningLow()));
        cells.add(formatDouble(row.warningHigh()));
        cells.add(formatDouble(row.criticalLow()));
        cells.add(formatDouble(row.criticalHigh()));
        cells.add(row.interlockRuleKey() == null ? "" : row.interlockRuleKey());
        cells.add(row.lifecycle() == null ? "DRAFT" : row.lifecycle());
        return String.join(",", cells.stream().map(OnboardingTemplateCsvRenderer::escape).toList());
    }

    private static String formatDouble(Double value) {
        if (value == null) {
            return "";
        }
        return String.format(Locale.US, "%s", value);
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%s", value);
    }

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        boolean quoted = raw.indexOf(',') >= 0 || raw.indexOf('"') >= 0 || raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0;
        if (!quoted) {
            return raw;
        }
        return '"' + raw.replace("\"", "\"\"") + '"';
    }
}