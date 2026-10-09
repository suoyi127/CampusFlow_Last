package com.campusflow.system;

import jakarta.validation.constraints.*;

public record ConfigInput(@NotNull @Min(1) Long expectedVersion,
    @NotNull @Min(2) @Max(60) Integer simulationSeconds,
    @NotNull @Min(5) @Max(300) Integer validitySeconds,
    @NotNull @Min(5) @Max(600) Integer noiseWindowSeconds,
    @NotNull Double distanceWeight,@NotNull Double quietWeight,@NotNull Double freeWeight,@NotNull Double facilityWeight) {}
