package com.factory.central.command;

import java.util.UUID;

public record CommandAcceptedResponse(UUID commandId, String status) {}
