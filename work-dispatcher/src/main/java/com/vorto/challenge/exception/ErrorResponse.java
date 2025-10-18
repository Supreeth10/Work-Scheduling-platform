package com.vorto.challenge.exception;

import java.time.OffsetDateTime;
import java.util.Map;

public record ErrorResponse(String code,
                            String message,
                            int status,
                            String path,
                            String correlationId,
                            OffsetDateTime timestamp,
                            Map<String, Object> details) {
}
