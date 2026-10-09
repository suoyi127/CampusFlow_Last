package com.campusflow.review;

public record ReviewSummary(long count,Double environmentAverage,Double facilityAverage) {
    public static ReviewSummary empty() { return new ReviewSummary(0,null,null); }
}
