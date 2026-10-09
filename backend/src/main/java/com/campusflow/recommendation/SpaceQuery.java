package com.campusflow.recommendation;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public record SpaceQuery(String name, String type,
    @DecimalMin("-90") @DecimalMax("90") Double latitude,
    @DecimalMin("-180") @DecimalMax("180") Double longitude,
    @DecimalMin("0") @DecimalMax("1000000") Double maxDistance,
    @Min(1) @Max(5) Integer minQuiet,
    @DecimalMin("0") @DecimalMax("1") Double maxOccupancy,
    List<String> facilities, Boolean openOnly, Instant startAt,
    @Min(0) @Max(1440) Integer durationMinutes, String sort,
    @Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) { }
