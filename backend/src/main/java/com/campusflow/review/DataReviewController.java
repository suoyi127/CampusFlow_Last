package com.campusflow.review;

import com.campusflow.auth.AccountService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/data/reviews")
public class DataReviewController {
    private final ReviewService reviews;
    private final AccountService accounts;
    public DataReviewController(ReviewService reviews,AccountService accounts) {this.reviews=reviews;this.accounts=accounts;}
    @GetMapping Map<String,Object> list(@Valid @ModelAttribute ReviewQuery query,Authentication auth) {return reviews.list(query,accounts.current(auth.getName()),true);}
    @PostMapping("/{id}/decision") ReviewView decide(@PathVariable long id,@Valid @RequestBody ReviewDecision input,Authentication auth) {return reviews.decide(id,input,accounts.current(auth.getName()));}
}
