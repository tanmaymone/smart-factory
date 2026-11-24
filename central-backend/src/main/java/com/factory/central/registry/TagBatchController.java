package com.factory.central.registry;

import com.factory.central.access.ActorContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registry/tags")
public class TagBatchController {

    private final TagBatchService tagBatchService;

    public TagBatchController(TagBatchService tagBatchService) {
        this.tagBatchService = tagBatchService;
    }

    @PostMapping(path = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TagBatchResponse importJson(@RequestBody JsonNode body) {
        List<TagBatchRowInput> rows = parseJsonRows(body);
        return tagBatchService.importBatch(ActorContext.anonymous(), rows);
    }

    @PostMapping(
            path = "/batch",
            consumes = {"text/csv", "text/plain"})
    public TagBatchResponse importCsv(@RequestBody(required = false) String body) {
        return tagBatchService.importCsv(ActorContext.anonymous(), body == null ? "" : body);
    }

    private List<TagBatchRowInput> parseJsonRows(JsonNode body) {
        if (body == null || body.isNull()) {
            throw new RegistryException(
                    RegistryErrorCode.VALIDATION_FAILED,
                    "Request body is required",
                    HttpStatus.BAD_REQUEST);
        }
        JsonNode arrayNode;
        if (body.isArray()) {
            arrayNode = body;
        } else if (body.isObject() && body.has("tags") && body.get("tags").isArray()) {
            arrayNode = body.get("tags");
        } else {
            throw new RegistryException(
                    RegistryErrorCode.VALIDATION_FAILED,
                    "Body must be a tag array or {\"tags\":[...]}",
                    HttpStatus.BAD_REQUEST,
                    List.of(new RegistryErrorResponse.FieldErrorDetail(
                            "tags", "must be an array")));
        }
        List<TagBatchRowInput> rows = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            if (!node.isObject()) {
                throw new RegistryException(
                        RegistryErrorCode.VALIDATION_FAILED,
                        "Each tag row must be an object",
                        HttpStatus.BAD_REQUEST);
            }
            Map<String, Object> map = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode value = entry.getValue();
                if (value == null || value.isNull()) {
                    map.put(entry.getKey(), null);
                } else if (value.isValueNode()) {
                    map.put(entry.getKey(), value.asText());
                } else {
                    map.put(entry.getKey(), value.toString());
                }
            }
            rows.add(TagBatchRowInput.fromMap(map));
        }
        return rows;
    }
}
