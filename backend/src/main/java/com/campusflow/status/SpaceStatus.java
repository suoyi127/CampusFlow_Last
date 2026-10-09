package com.campusflow.status;

import java.time.Instant;

public record SpaceStatus(long spaceId, long simulationRunId, Long snapshotVersion, Integer currentPeople,
    int capacity, Double occupancyRate, Double noiseDb, Double typicalNoiseDb, Integer quietLevel,
    String peopleState, String noiseState, String deviceState, Instant peopleUpdatedAt,
    Instant noiseUpdatedAt, Instant calculatedAt, int validitySeconds, String source) { }
