package com.campusflow.recommendation;

import com.campusflow.common.BusinessException;
import com.campusflow.space.*;
import com.campusflow.status.StatusService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class RecommendationService {
    private final SpaceService spaces;
    private final StatusService statuses;
    private final Clock clock;
    public RecommendationService(SpaceService spaces, StatusService statuses, Clock clock) {
        this.spaces = spaces; this.statuses = statuses; this.clock = clock;
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ) public Map<String,Object> list(SpaceQuery query) {
        double latitude = query.latitude() == null ? 31.2304 : query.latitude();
        double longitude = query.longitude() == null ? 121.4737 : query.longitude();
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || (query.maxDistance() != null && !Double.isFinite(query.maxDistance()))
            || (query.maxOccupancy() != null && !Double.isFinite(query.maxOccupancy())))
            throw new BusinessException(400,"INVALID_INPUT","查询参数必须为有限数值");
        var required = query.facilities() == null ? List.<String>of() : query.facilities();
        if (!Set.of("AC","SEAT","POWER","WIFI").containsAll(required)) throw new BusinessException(400,"INVALID_FACILITY","设施类型无效");
        Instant now = clock.instant();
        Instant start = query.startAt() == null ? now : query.startAt();
        int duration = query.durationMinutes() == null ? 0 : query.durationMinutes();
        boolean checkOpen = query.startAt() != null || duration > 0 || !Boolean.FALSE.equals(query.openOnly());
        List<SpaceCard> cards = new ArrayList<>();
        for (var space : spaces.all()) {
            if (!space.enabled) continue;
            if (query.name() != null && !space.name.contains(query.name().trim())) continue;
            if (query.type() != null && !query.type().isEmpty() && !space.type.equals(query.type())) continue;
            if (!facilities(space).containsAll(required)) continue;
            if (checkOpen && !OpeningHours.covers(space, start, duration)) continue;
            var card = card(space, latitude, longitude, now);
            if (card.distanceMeters() > (query.maxDistance() == null ? 3000 : query.maxDistance())) continue;
            if (query.minQuiet() != null && (card.status().quietLevel() == null || card.status().quietLevel() < query.minQuiet())) continue;
            if (query.maxOccupancy() != null && (card.status().occupancyRate() == null || card.status().occupancyRate() > query.maxOccupancy())) continue;
            cards.add(card);
        }
        String sort = query.sort() == null ? "SCORE" : query.sort();
        Comparator<SpaceCard> order = switch (sort) {
            case "SCORE" -> Comparator.comparingDouble(SpaceCard::score).reversed().thenComparingDouble(SpaceCard::distanceMeters);
            case "DISTANCE" -> Comparator.comparingDouble(SpaceCard::distanceMeters);
            case "QUIET" -> Comparator.comparing(c -> c.status().typicalNoiseDb(), Comparator.nullsLast(Comparator.naturalOrder()));
            case "OCCUPANCY" -> Comparator.comparing(c -> c.status().occupancyRate(), Comparator.nullsLast(Comparator.naturalOrder()));
            case "FACILITY" -> Comparator.<SpaceCard>comparingInt(c -> facilities(c.space()).size()).reversed();
            default -> throw new BusinessException(400,"INVALID_SORT","排序方式无效");
        };
        cards.sort(order.thenComparingLong(c -> c.space().id));
        int page = query.page() == null ? 1 : query.page();
        int size = query.pageSize() == null ? 20 : query.pageSize();
        int from = (int)Math.min(cards.size(), (long)(page - 1) * size);
        var warnings = new ArrayList<String>();
        if (start.isAfter(now.plusSeconds(60))) warnings.add("人数和噪声反映当前状态，不代表预计到达时的状态");
        if (cards.isEmpty()) warnings.add("没有空间满足全部条件；可扩大距离、放宽安静或拥挤要求、取消部分设施。缺失实时数据不能证明满足实时条件。");
        return Map.of("items", cards.subList(from, Math.min(cards.size(), from + size)), "total", cards.size(),
            "page", page, "pageSize", size, "calculatedAt", now, "warnings", warnings);
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ) public SpaceCard detail(long id, double latitude, double longitude) {
        var space = spaces.get(id);
        if (!space.enabled) throw new BusinessException(409,"SPACE_DISABLED","空间已停用，暂不可查询或提交新评价");
        return card(space, latitude, longitude, clock.instant());
    }
    private SpaceCard card(StudySpace space, double latitude, double longitude, Instant now) {
        var status = statuses.current(space, now);
        double distance = distance(latitude, longitude, space.latitude, space.longitude);
        double quiet = status.quietLevel() == null ? 0 : (status.quietLevel() - 1) / 4.0;
        double free = status.occupancyRate() == null ? 0 : 1 - status.occupancyRate();
        double score = (Math.max(0, 1 - distance / 3000) * .30 + quiet * .30 + free * .25 + facilities(space).size() / 4.0 * .15) * 100;
        var reasons = new ArrayList<String>();
        reasons.add("直线距离 " + Math.round(distance) + " 米");
        reasons.add(status.quietLevel() == null ? "暂无近期噪声数据" : "安静等级 " + status.quietLevel() + "/5");
        reasons.add(status.occupancyRate() == null ? "暂无近期人数数据" : "拥挤率 " + Math.round(status.occupancyRate() * 100) + "%");
        if (facilities(space).contains("POWER")) reasons.add("具备插座");
        return new SpaceCard(space, status, distance, score, OpeningHours.covers(space, now, 0), reasons);
    }
    static Set<String> facilities(StudySpace space) {
        return space.facilities.isBlank() ? Set.of() : new HashSet<>(Arrays.asList(space.facilities.split(",")));
    }
    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        double lat = Math.toRadians(lat2 - lat1), lon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(lat/2),2) + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.pow(Math.sin(lon/2),2);
        return 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
    }
}
