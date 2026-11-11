package com.ganjunbao.transaction.dto;

import java.time.LocalDateTime;
import java.util.Map;

public class ErrorResponse {

    private final LocalDateTime timestamp;
    private final int status;
    private final String code;
    private final String message;
    private final Map<String, String> errors;

    public ErrorResponse(
            LocalDateTime timestamp,
            int status,
            String code,
            String message,
            Map<String, String> errors
    ) {
        this.timestamp = timestamp;
        this.status = status;
        this.code = code;
        this.message = message;
        this.errors = errors;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}