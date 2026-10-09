package com.campusflow.system;

import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

public record LogQuery(@Size(max=64) String actor,@Size(max=64) String action,
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate updatedFrom,
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate updatedTo,
    @Min(1) @Max(1000000) Integer page,@Min(1) @Max(100) Integer pageSize) {}
