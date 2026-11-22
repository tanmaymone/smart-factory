package com.factory.edge.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EdgeDiagnosticsController {

    private final EdgeStatusService statusService;

    public EdgeDiagnosticsController(EdgeStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/api/edge/status")
    public EdgeStatusSnapshot status() {
        return statusService.snapshot();
    }
}
