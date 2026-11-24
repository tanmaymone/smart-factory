package com.factory.central.registry;

import com.factory.central.access.ActorContextHolder;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registry/sites")
public class SiteRegistryController {

    private final SiteRegistryService siteRegistryService;

    public SiteRegistryController(SiteRegistryService siteRegistryService) {
        this.siteRegistryService = siteRegistryService;
    }

    @GetMapping
    public List<SiteResponse> listSites() {
        return siteRegistryService.list();
    }

    @GetMapping("/{siteCode}")
    public SiteResponse getSite(@PathVariable String siteCode) {
        return siteRegistryService.get(siteCode);
    }

    @PostMapping
    public ResponseEntity<SiteResponse> createSite(@Valid @RequestBody SiteRequest request) {
        SiteRegistryService.SiteCreateResult result =
                siteRegistryService.create(ActorContextHolder.current(), request);
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/registry/sites/" + result.body().siteCode()))
                    .body(result.body());
        }
        return ResponseEntity.ok(result.body());
    }

    @PutMapping("/{siteCode}")
    public SiteResponse updateSite(
            @PathVariable String siteCode, @Valid @RequestBody SiteUpdateRequest request) {
        return siteRegistryService.update(ActorContextHolder.current(), siteCode, request);
    }
}
