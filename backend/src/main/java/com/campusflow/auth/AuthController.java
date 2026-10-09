package com.campusflow.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;
    private final RegistrationService registration;
    public AuthController(AccountService accounts,RegistrationService registration) { this.accounts = accounts;this.registration=registration; }
    @PostMapping("/register") @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    AccountView register(@jakarta.validation.Valid @RequestBody RegistrationInput input) { return registration.register(input); }
    @GetMapping("/csrf") Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }
    @GetMapping("/me") Account me(Authentication authentication) { return accounts.current(authentication.getName()); }
}
