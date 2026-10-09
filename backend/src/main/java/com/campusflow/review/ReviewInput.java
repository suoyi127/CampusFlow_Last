package com.campusflow.review;

import jakarta.validation.constraints.*;

public record ReviewInput(
    @NotNull(message="请填写环境评分") @Min(value=1,message="环境评分须为1至5分") @Max(value=5,message="环境评分须为1至5分") Integer environmentScore,
    @NotNull(message="请填写设施评分") @Min(value=1,message="设施评分须为1至5分") @Max(value=5,message="设施评分须为1至5分") Integer facilityScore,
    @NotNull(message="评价文字可为空字符串") @Size(max=500,message="评价文字不能超过500字") String content,
    @NotNull(message="请携带当前评价版本") @Min(value=0,message="评价版本无效") Long expectedVersion
) { }
