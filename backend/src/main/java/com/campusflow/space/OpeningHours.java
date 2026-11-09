package com.campusflow.space;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;

public final class OpeningHours {
    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");

    private OpeningHours() { }

    public static boolean covers(StudySpace space, Instant start, int durationMinutes) {
        var from = start.atZone(CAMPUS_ZONE);
        var to = from.plusMinutes(durationMinutes);
        var openDays = Arrays.asList(space.openDays.split(","));
        if (!openDays.contains(Integer.toString(from.getDayOfWeek().getValue()))) {
            return false;
        }

        if (space.allDay) {
            // 结束时间恰好为午夜时，学习时段不占用次日。
            var lastDay = durationMinutes == 0 ? from.toLocalDate() : to.minusNanos(1).toLocalDate();
            for (var day = from.toLocalDate(); day.isBefore(lastDay); ) {
                day = day.plusDays(1);
                if (!openDays.contains(Integer.toString(day.getDayOfWeek().getValue()))) {
                    return false;
                }
            }
            return true;
        }

        var closing = LocalTime.parse(space.closeTime);
        return from.toLocalDate().equals(to.toLocalDate())
            && !from.toLocalTime().isBefore(LocalTime.parse(space.openTime))
            && (durationMinutes == 0 ? from.toLocalTime().isBefore(closing)
                : !to.toLocalTime().isAfter(closing));
    }
}
