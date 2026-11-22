package com.factory.central.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = StreamController.class)
public class StreamExceptionHandler {

    private final ObjectMapper objectMapper;

    public StreamExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(StreamAtCapacityException.class)
    public void handleCapacity(StreamAtCapacityException ex, HttpServletResponse response) throws IOException {
        // EventSource sends Accept: text/event-stream; write JSON 503 explicitly so negotiation cannot block it.
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                StreamErrorResponse.of(StreamErrorCode.STREAM_AT_CAPACITY, ex.getMessage()));
    }
}
