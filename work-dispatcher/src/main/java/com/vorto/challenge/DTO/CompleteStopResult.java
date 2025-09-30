package com.vorto.challenge.DTO;

public record CompleteStopResult(
        LoadAssignmentResponse completed,
        LoadAssignmentResponse nextAssignment // may be null
) {}
