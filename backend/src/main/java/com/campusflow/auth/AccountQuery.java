package com.campusflow.auth;

import jakarta.validation.constraints.*;

public record AccountQuery(@Size(max=64) String username,@Pattern(regexp="USER|DATA_ADMIN|SERVER_ADMIN") String role,
    Boolean enabled,@Min(1) @Max(1000000) Integer page,@Min(1) @Max(100) Integer pageSize) { }
