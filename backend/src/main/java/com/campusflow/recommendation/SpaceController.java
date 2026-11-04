package com.campusflow.recommendation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/spaces")
public class SpaceController {
    private final RecommendationService recommendations;
    public SpaceController(RecommendationService recommendations) { this.recommendations = recommendations; }
    @GetMapping Map<String,Object> list(@Valid @ModelAttribute SpaceQuery query) { return recommendations.list(query); }
    @GetMapping("/{id}") SpaceCard detail(@PathVariable long id,
        @RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude,
        @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude) {
        return recommendations.detail(id,latitude,longitude);
    }
}
