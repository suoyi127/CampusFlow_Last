package com.campusflow.hardware;
import jakarta.validation.constraints.*;
public record HardwareDeviceInput(
 @NotNull @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String deviceId,
 @NotBlank @Size(max=100) String name, @Positive Long spaceId,
 @NotNull Boolean enabled, @Positive Long version) {}
