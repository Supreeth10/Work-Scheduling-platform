package com.vorto.challenge.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Driver login request by username")
public record LoginRequest(
        @Schema(example = "rama")
        @NotBlank(message = "username is required")
        @Size(max = 64, message = "username must be <= 64 characters")
        @Pattern(
                regexp = "^[\\p{L}\\p{N} ._'-]+$",
                message = "username contains invalid characters"
        )
        String username
) { }
