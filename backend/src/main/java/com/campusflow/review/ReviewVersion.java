package com.campusflow.review;

import jakarta.validation.constraints.*;

public record ReviewVersion(@NotNull(message="请携带当前评价版本") @Min(1) Long expectedVersion) { }
