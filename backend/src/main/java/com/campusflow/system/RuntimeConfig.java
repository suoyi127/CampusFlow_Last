package com.campusflow.system;

public record RuntimeConfig(long version,int validitySeconds,int noiseWindowSeconds,
    double distanceWeight,double quietWeight,double freeWeight,double facilityWeight) {}
