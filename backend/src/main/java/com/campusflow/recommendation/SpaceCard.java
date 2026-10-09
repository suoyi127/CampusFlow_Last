package com.campusflow.recommendation;

import com.campusflow.space.StudySpace;
import com.campusflow.status.SpaceStatus;
import java.util.List;

public record SpaceCard(StudySpace space, SpaceStatus status, double distanceMeters, double score,
    boolean openNow, List<String> reasons) { }
