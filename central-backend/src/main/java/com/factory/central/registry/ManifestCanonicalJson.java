package com.factory.central.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central-only canonical writer for the edge config {@code lines} array. Hash excludes
 * {@code version} and {@code generatedAt}. Edge must not re-implement this.
 */
public final class ManifestCanonicalJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ManifestCanonicalJson() {}

    public static String hashLines(List<EdgeConfigLine> lines) {
        try {
            String canonical = MAPPER.writeValueAsString(toCanonicalLines(lines));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to canonicalize manifest lines", ex);
        }
    }

    static List<Map<String, Object>> toCanonicalLines(List<EdgeConfigLine> lines) {
        List<EdgeConfigLine> orderedLines = new ArrayList<>(lines == null ? List.of() : lines);
        orderedLines.sort(Comparator.comparing(EdgeConfigLine::lineId));
        List<Map<String, Object>> out = new ArrayList<>(orderedLines.size());
        for (EdgeConfigLine line : orderedLines) {
            Map<String, Object> lineMap = new LinkedHashMap<>();
            lineMap.put("lineId", line.lineId());
            List<EdgeConfigTag> tags = new ArrayList<>(line.tags() == null ? List.of() : line.tags());
            tags.sort(Comparator.comparing(EdgeConfigTag::tagCode));
            List<Map<String, Object>> tagMaps = new ArrayList<>(tags.size());
            for (EdgeConfigTag tag : tags) {
                Map<String, Object> tagMap = new LinkedHashMap<>();
                tagMap.put("tagCode", tag.tagCode());
                tagMap.put("protocol", tag.protocol());
                tagMap.put("externalAddress", tag.externalAddress());
                tagMap.put("dataType", tag.dataType());
                tagMap.put("writable", tag.writable());
                tagMap.put("deadband", deadbandPlain(tag.deadband()));
                tagMap.put("endpointUrl", tag.endpointUrl());
                tagMaps.add(tagMap);
            }
            lineMap.put("tags", tagMaps);
            out.add(lineMap);
        }
        return out;
    }

    static String deadbandPlain(double deadband) {
        return BigDecimal.valueOf(deadband).stripTrailingZeros().toPlainString();
    }
}
