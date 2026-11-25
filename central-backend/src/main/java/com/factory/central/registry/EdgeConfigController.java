package com.factory.central.registry;

import java.util.ArrayList;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/edge")
public class EdgeConfigController {

    private final EdgeConfigManifestService manifestService;

    public EdgeConfigController(EdgeConfigManifestService manifestService) {
        this.manifestService = manifestService;
    }

    @GetMapping(value = "/{siteId}/config", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EdgeConfigManifest> getConfig(
            @PathVariable String siteId,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        String normalized = manifestService.requireValidSiteId(siteId);
        long version = manifestService.resolveVersion(normalized);
        String etag = etag(version);
        if (matchesIfNoneMatch(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(CacheControl.noCache())
                    .build();
        }
        EdgeConfigManifest body = manifestService.buildManifest(normalized);
        return ResponseEntity.ok()
                .eTag(etag(body.version()))
                .cacheControl(CacheControl.noCache())
                .body(body);
    }

    static String etag(long version) {
        return "v" + version;
    }

    /**
     * Split on commas, trim tokens, ignore weak validators. Empty / whitespace-only header
     * means no precondition (caller returns 200).
     */
    static boolean matchesIfNoneMatch(String header, String strongEtag) {
        if (header == null || header.isBlank()) {
            return false;
        }
        List<String> tokens = splitHeader(header);
        if (tokens.isEmpty()) {
            return false;
        }
        String expected = stripQuotes(strongEtag);
        for (String token : tokens) {
            if (token.startsWith("W/") || token.startsWith("w/")) {
                continue;
            }
            if (expected.equals(stripQuotes(token))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> splitHeader(String header) {
        List<String> tokens = new ArrayList<>();
        for (String part : header.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }

    private static String stripQuotes(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        if (v.length() >= 2 && v.charAt(0) == '"' && v.charAt(v.length() - 1) == '"') {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }
}
