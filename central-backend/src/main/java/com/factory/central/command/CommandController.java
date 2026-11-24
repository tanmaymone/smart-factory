package com.factory.central.command;

import com.factory.central.access.ActorContextHolder;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/commands")
public class CommandController {

    private final CommandService commandService;

    public CommandController(CommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping
    public ResponseEntity<CommandAcceptedResponse> submit(@Valid @RequestBody CommandRequest request) {
        CommandAcceptedResponse response = commandService.submit(ActorContextHolder.current(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/audit")
    public List<CommandAuditResponse> audit(
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String lineId,
            @RequestParam(required = false) String tagCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return commandService.listAudit(siteId, lineId, tagCode, status, page, size);
    }
}
