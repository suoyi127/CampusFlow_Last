package com.campusflow.space;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpeningHoursTest {
    private final Instant monday2330 = Instant.parse("2026-11-09T15:30:00Z");

    @Test
    void allowsOvernightStudyOnlyAcrossOpenAllDayDates() {
        var space = new StudySpace();
        space.allDay = true;
        space.openDays = "1,2";
        assertTrue(OpeningHours.covers(space, monday2330, 90));

        space.openDays = "1";
        assertTrue(OpeningHours.covers(space, monday2330, 30));
        assertFalse(OpeningHours.covers(space, monday2330, 31));
    }
}
