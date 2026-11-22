package com.factory.central.command;

import com.factory.central.registry.TagMapping;
import com.factory.central.registry.TagMappingRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CommandTagResolver {

    private final TagMappingRepository tagMappingRepository;

    public CommandTagResolver(TagMappingRepository tagMappingRepository) {
        this.tagMappingRepository = tagMappingRepository;
    }

    public TagMapping resolve(CommandRequest request) {
        boolean hasTagId = request.tagId() != null;
        String tagCode = request.tagCode() == null ? null : request.tagCode().trim();
        boolean hasTagCode = tagCode != null && !tagCode.isEmpty();
        if (hasTagId == hasTagCode) {
            throw new CommandException(
                    CommandErrorCode.VALIDATION_FAILED,
                    "Exactly one of tagId or tagCode is required",
                    HttpStatus.BAD_REQUEST,
                    null,
                    List.of(new CommandErrorResponse.FieldErrorDetail(
                            "tagCode", "provide exactly one of tagId or tagCode")));
        }
        if (hasTagId) {
            TagMapping tag = tagMappingRepository
                    .findById(request.tagId())
                    .orElseThrow(() -> new CommandException(
                            CommandErrorCode.TAG_NOT_FOUND,
                            "Tag not found: " + request.tagId(),
                            HttpStatus.UNPROCESSABLE_ENTITY));
            if (!tag.getSiteId().equals(request.siteId()) || !tag.getLineId().equals(request.lineId())) {
                throw new CommandException(
                        CommandErrorCode.TAG_NOT_FOUND,
                        "Tag not found for site/line",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            return tag;
        }
        return tagMappingRepository
                .findBySiteIdAndLineIdAndTagCode(request.siteId(), request.lineId(), tagCode)
                .orElseThrow(() -> new CommandException(
                        CommandErrorCode.TAG_NOT_FOUND,
                        "Tag not found: " + tagCode,
                        HttpStatus.UNPROCESSABLE_ENTITY));
    }
}
