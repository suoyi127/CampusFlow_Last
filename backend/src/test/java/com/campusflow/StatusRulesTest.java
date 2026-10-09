package com.campusflow;

import com.campusflow.status.StatusService;
import com.campusflow.space.*;
import com.campusflow.recommendation.RecommendationService;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StatusRulesTest {
    @Test void expiryRetainsThirtySecondBoundaryAndMissingIsUnknown() {
        var now = Instant.parse("2026-10-09T04:00:00Z");
        assertEquals("VALID", StatusService.state(now.minusSeconds(30),now));
        assertEquals("EXPIRED", StatusService.state(now.minusSeconds(31),now));
        assertEquals("MISSING", StatusService.state(null,now));
        assertEquals("EXPIRED", StatusService.state(now.plusSeconds(1),now));
    }
    @Test void medianAndQuietThresholdsFollowRequirements() {
        assertEquals(50, StatusService.median(List.of(30.0,50.0,90.0)));
        assertEquals(55, StatusService.median(List.of(30.0,50.0,60.0,90.0)));
        assertEquals(5, StatusService.quietLevel(44.9));
        assertEquals(4, StatusService.quietLevel(45));
        assertEquals(3, StatusService.quietLevel(55));
        assertEquals(2, StatusService.quietLevel(65));
        assertEquals(1, StatusService.quietLevel(75));
    }
    @Test void openingMustCoverEntireStudyIntervalInShanghai() {
        var space = new StudySpace(); space.openDays="1,2,3,4,5"; space.allDay=false;
        space.openTime="08:00"; space.closeTime="22:00";
        assertTrue(OpeningHours.covers(space,Instant.parse("2026-10-09T13:00:00Z"),60));
        assertFalse(OpeningHours.covers(space,Instant.parse("2026-10-09T13:00:00Z"),61));
        assertFalse(OpeningHours.covers(space,Instant.parse("2026-10-09T14:00:00Z"),0));
        assertFalse(OpeningHours.covers(space,Instant.parse("2026-10-10T04:00:00Z"),30));
    }
    @Test void distanceUsesFixedStraightLineMeters() {
        assertEquals(0,RecommendationService.distance(31.23,121.47,31.23,121.47),0.000001);
        assertEquals(111195,RecommendationService.distance(0,0,1,0),1);
    }
}
