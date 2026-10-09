package com.campusflow.review;

import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class PublicReviewController {
    private final ReviewService reviews;
    public PublicReviewController(ReviewService reviews) {this.reviews=reviews;}
    @GetMapping("/api/spaces/{spaceId}/reviews") Map<String,Object> published(@PathVariable long spaceId,
        @RequestParam(defaultValue="1") @Min(1) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {return reviews.published(spaceId,page,pageSize);}
}
