package com.factory.central.stream;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/stream")
public class StreamController {

    private final TelemetryStreamHub hub;

    public StreamController(TelemetryStreamHub hub) {
        this.hub = hub;
    }

    @GetMapping(path = "/telemetry", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter telemetry(
            @RequestParam(required = false) String siteId, @RequestParam(required = false) String lineId) {
        return hub.subscribe(siteId, lineId);
    }
}
