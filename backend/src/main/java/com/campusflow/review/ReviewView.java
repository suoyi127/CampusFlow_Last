package com.campusflow.review;

import java.time.Instant;

public record ReviewView(long id,long userId,String username,long spaceId,String spaceName,boolean spaceEnabled,
    int environmentScore,int facilityScore,String content,String status,Long reviewedBy,String reviewerName,
    Instant reviewedAt,String reviewReason,Instant updatedAt,long version) { }
