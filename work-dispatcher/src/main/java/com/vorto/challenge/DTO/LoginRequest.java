package com.vorto.challenge.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "username is required")
        @Size(max = 64, message = "username must be <= 64 characters")
        @Pattern(
                regexp = "^[\\p{L}\\p{N} ._'-]+$",
                message = "username contains invalid characters"
        )
        String username
) { }
