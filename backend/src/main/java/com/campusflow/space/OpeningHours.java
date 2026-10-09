package com.campusflow.space;

import java.time.*;
import java.util.Arrays;

public final class OpeningHours {
    private OpeningHours() { }
    public static boolean covers(StudySpace space, Instant start, int durationMinutes) {
        var from = start.atZone(ZoneId.of("Asia/Shanghai"));
        var to = from.plusMinutes(durationMinutes);
        if (!from.toLocalDate().equals(to.toLocalDate())) return false;
        if (Arrays.stream(space.openDays.split(",")).noneMatch(day -> day.equals("" + from.getDayOfWeek().getValue()))) return false;
        if (space.allDay) return true;
        return !from.toLocalTime().isBefore(LocalTime.parse(space.openTime))
            && (durationMinutes == 0 ? from.toLocalTime().isBefore(LocalTime.parse(space.closeTime))
                : !to.toLocalTime().isAfter(LocalTime.parse(space.closeTime)));
    }
}
