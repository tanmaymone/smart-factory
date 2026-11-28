package com.factory.central.onboarding;

import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/onboarding")
public class OnboardingChecklistController {

    private final OnboardingChecklistCatalog catalog;

    public OnboardingChecklistController(OnboardingChecklistCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping(value = "/checklist", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PreCutoverChecklistDtos.PreCutoverChecklistResponse> checklist(
            @RequestParam(required = false) String category) {
        Optional<PreCutoverChecklistDtos.ChecklistCategory> filter = Optional.empty();
        if (category != null && !category.isBlank()) {
            filter = Optional.of(catalog.parseCategory(category));
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(catalog.response(filter));
    }
}