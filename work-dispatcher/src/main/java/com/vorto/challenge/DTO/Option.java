package com.vorto.challenge.DTO;

import java.util.UUID;

public record Option(UUID driverId, UUID l1Id, UUID l2IdOrNull, double totalCostMeters) {
}
