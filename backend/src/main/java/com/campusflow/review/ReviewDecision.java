package com.campusflow.review;

import jakarta.validation.constraints.*;

public record ReviewDecision(
    @NotNull @Pattern(regexp="APPROVE|REJECT|REVOKE",message="审核动作无效") String action,
    @NotNull @Size(max=500,message="审核原因不能超过500字") String reason,
    @NotNull(message="请携带待审核版本") @Min(1) Long expectedVersion
) { }
