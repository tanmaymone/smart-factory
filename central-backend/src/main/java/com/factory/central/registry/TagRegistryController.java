package com.factory.central.registry;

import com.factory.central.access.ActorContextHolder;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registry/tags")
public class TagRegistryController {

    private final TagRegistryService tagRegistryService;

    public TagRegistryController(TagRegistryService tagRegistryService) {
        this.tagRegistryService = tagRegistryService;
    }

    @GetMapping
    public List<TagMappingResponse> listTags(
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String lineId,
            @RequestParam(required = false) Protocol protocol,
            @RequestParam(required = false) Boolean writable) {
        return tagRegistryService.list(siteId, lineId, protocol, writable);
    }

    @PostMapping
    public ResponseEntity<TagMappingResponse> createTag(@Valid @RequestBody TagMappingRequest request) {
        TagMappingResponse created = tagRegistryService.create(ActorContextHolder.current(), request);
        return ResponseEntity.created(URI.create("/api/registry/tags/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public TagMappingResponse updateTag(
            @PathVariable UUID id, @Valid @RequestBody TagMappingRequest request) {
        return tagRegistryService.update(ActorContextHolder.current(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable UUID id) {
        tagRegistryService.delete(ActorContextHolder.current(), id);
        return ResponseEntity.noContent().build();
    }
}
