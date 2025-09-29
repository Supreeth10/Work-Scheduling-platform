package com.vorto.challenge.DTO;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "username is required")
        String username
) { }
