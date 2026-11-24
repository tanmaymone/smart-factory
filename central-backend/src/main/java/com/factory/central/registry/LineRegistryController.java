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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registry/lines")
public class LineRegistryController {

    private final LineRegistryService lineRegistryService;

    public LineRegistryController(LineRegistryService lineRegistryService) {
        this.lineRegistryService = lineRegistryService;
    }

    @GetMapping
    public List<LineResponse> listLines(@RequestParam(required = false) String siteCode) {
        return lineRegistryService.list(siteCode);
    }

    @GetMapping("/{siteCode}/{lineCode}")
    public LineResponse getLine(@PathVariable String siteCode, @PathVariable String lineCode) {
        return lineRegistryService.get(siteCode, lineCode);
    }

    @PostMapping
    public ResponseEntity<LineResponse> createLine(@Valid @RequestBody LineRequest request) {
        LineRegistryService.LineCreateResult result =
                lineRegistryService.create(ActorContextHolder.current(), request);
        if (result.created()) {
            return ResponseEntity.created(URI.create(
                            "/api/registry/lines/"
                                    + result.body().siteCode()
                                    + "/"
                                    + result.body().lineCode()))
                    .body(result.body());
        }
        return ResponseEntity.ok(result.body());
    }

    @PutMapping("/{siteCode}/{lineCode}")
    public LineResponse updateLine(
            @PathVariable String siteCode,
            @PathVariable String lineCode,
            @Valid @RequestBody LineUpdateRequest request) {
        return lineRegistryService.update(ActorContextHolder.current(), siteCode, lineCode, request);
    }
}
