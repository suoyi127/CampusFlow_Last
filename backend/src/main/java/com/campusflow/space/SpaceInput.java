package com.campusflow.space;

import jakarta.validation.constraints.*;
import java.util.List;

public record SpaceInput(
    @NotBlank(message="名称不能为空") @Size(max=100) String name,
    @NotBlank(message="类型不能为空") String type,
    @NotBlank(message="地址不能为空") @Size(max=200) String address,
    @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
    @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
    @NotNull @Min(value=1,message="容量必须为正整数") @Max(10000) Integer capacity,
    @NotNull @Pattern(regexp="([01]\\d|2[0-3]):[0-5]\\d",message="开放时间格式应为 HH:mm") String openTime,
    @NotNull @Pattern(regexp="([01]\\d|2[0-3]):[0-5]\\d",message="关闭时间格式应为 HH:mm") String closeTime,
    @NotEmpty(message="请选择开放日") List<@Min(1) @Max(7) Integer> openDays,
    @NotNull Boolean allDay,
    @NotNull List<String> facilities,
    @NotNull @Size(max=1000) String description,
    @NotNull Boolean enabled
) { }
