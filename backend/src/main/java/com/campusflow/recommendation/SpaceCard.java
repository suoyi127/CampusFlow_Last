package com.campusflow.recommendation;

import com.campusflow.space.StudySpace;
import com.campusflow.status.SpaceStatus;
import com.campusflow.review.ReviewSummary;
import java.util.List;

public record SpaceCard(StudySpace space, SpaceStatus status, double distanceMeters, double score,
    boolean openNow, List<String> reasons, ReviewSummary reviewSummary) { }
