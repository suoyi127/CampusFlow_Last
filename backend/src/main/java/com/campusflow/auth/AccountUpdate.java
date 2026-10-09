package com.campusflow.auth;

import jakarta.validation.constraints.*;

public record AccountUpdate(@NotNull @Pattern(regexp="USER|DATA_ADMIN|SERVER_ADMIN") String role,
    @NotNull Boolean enabled,@NotNull @Min(1) Long expectedVersion) { }
