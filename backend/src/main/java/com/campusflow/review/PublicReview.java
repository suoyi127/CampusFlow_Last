package com.campusflow.review;

import java.time.Instant;

public record PublicReview(long id,String author,int environmentScore,int facilityScore,String content,
    Instant updatedAt,Instant reviewedAt) { }
