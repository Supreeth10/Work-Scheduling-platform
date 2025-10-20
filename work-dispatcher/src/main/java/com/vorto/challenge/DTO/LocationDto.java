package com.vorto.challenge.DTO;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Geographic point")
public record LocationDto(@Schema(example = "33.4484") Double lat,
                          @Schema(example = "-112.0740") Double lng) {
}
