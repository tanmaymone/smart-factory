package com.factory.central.access;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/access/actors")
public class ActorAdminController {

    private final ActorAdminService actorAdminService;

    public ActorAdminController(ActorAdminService actorAdminService) {
        this.actorAdminService = actorAdminService;
    }

    @GetMapping
    public List<ActorResponse> listActors() {
        return actorAdminService.list();
    }

    @PostMapping
    public ResponseEntity<ActorResponse> upsertActor(@Valid @RequestBody ActorRequest request) {
        ActorAdminService.ActorUpsertResult result = actorAdminService.upsert(request);
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/access/actors/" + result.body().actorId()))
                    .body(result.body());
        }
        return ResponseEntity.ok(result.body());
    }
}
