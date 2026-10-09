package com.campusflow.hardware;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public final class HardwarePayload {
    private HardwarePayload() {}
    public enum EventType { CHECK_IN, CHECK_OUT }
    public enum Kind { EVENT, TELEMETRY }
    public record Event(
        @JsonProperty("device_id") @NotNull @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String deviceId,
        @JsonProperty("event_id") @NotNull @Pattern(regexp="[1-9][0-9]{0,19}") String eventId,
        @NotNull @Min(946684800) @Max(4102444799L) Long timestamp,
        @JsonProperty("event_type") @NotNull EventType eventType,
        @JsonProperty("headcount_after_event") @NotNull @Min(0) @Max(4294967295L) Long headcount) {}
    public record Telemetry(
        @JsonProperty("device_id") @NotNull @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String deviceId,
        @NotNull @Min(946684800) @Max(4102444799L) Long timestamp,
        @NotNull @Min(0) @Max(4294967295L) Long headcount,
        @DecimalMin("0") @DecimalMax("150") Double decibel,
        @JsonProperty("sound_valid") @NotNull Boolean soundValid) {}
    public record Receipt(long recordId, long spaceId, String deviceId, boolean duplicate) {}
}
