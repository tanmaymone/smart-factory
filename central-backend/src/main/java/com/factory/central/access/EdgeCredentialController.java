package com.factory.central.access;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/access/edge-credentials")
public class EdgeCredentialController {

    private final EdgeCredentialService edgeCredentialService;

    public EdgeCredentialController(EdgeCredentialService edgeCredentialService) {
        this.edgeCredentialService = edgeCredentialService;
    }

    @GetMapping
    public List<EdgeCredentialSummaryResponse> list(@RequestParam String siteId) {
        return edgeCredentialService.list(siteId);
    }

    @PostMapping
    public ResponseEntity<EdgeCredentialCreatedResponse> create(@Valid @RequestBody EdgeCredentialCreateRequest request) {
        ActorContext actor = ActorContextHolder.current();
        EdgeCredentialCreatedResponse body =
                edgeCredentialService.create(request, actor.actorId() == null ? "system" : actor.actorId());
        return ResponseEntity.created(URI.create("/api/access/edge-credentials/" + body.id()))
                .body(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        edgeCredentialService.revoke(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}