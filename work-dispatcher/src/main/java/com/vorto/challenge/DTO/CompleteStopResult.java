package com.vorto.challenge.DTO;

import io.swagger.v3.oas.annotations.media.Schema;

public record CompleteStopResult(
        @Schema(description = "The assignment after completion")
        LoadAssignmentResponse completed,
        @Schema(description = "If the previous load completed, a new auto-assigned load (may be null)")
        LoadAssignmentResponse nextAssignment // may be null
) {}
