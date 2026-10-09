package com.campusflow.review;

import com.campusflow.auth.AccountService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/user/reviews")
public class UserReviewController {
    private final ReviewService reviews;
    private final AccountService accounts;
    public UserReviewController(ReviewService reviews,AccountService accounts) {this.reviews=reviews;this.accounts=accounts;}
    @GetMapping Map<String,Object> list(@Valid @ModelAttribute ReviewQuery query,Authentication auth) {return reviews.list(query,accounts.current(auth.getName()),false);}
    @GetMapping("/{spaceId}") Map<String,Object> mine(@PathVariable long spaceId,Authentication auth) {return reviews.mine(spaceId,accounts.current(auth.getName()));}
    @PutMapping("/{spaceId}") ReviewView submit(@PathVariable long spaceId,@Valid @RequestBody ReviewInput input,Authentication auth) {return reviews.submit(spaceId,input,accounts.current(auth.getName()));}
    @PostMapping("/{spaceId}/withdraw") ReviewView withdraw(@PathVariable long spaceId,@Valid @RequestBody ReviewVersion input,Authentication auth) {return reviews.withdraw(spaceId,input.expectedVersion(),accounts.current(auth.getName()));}
}
