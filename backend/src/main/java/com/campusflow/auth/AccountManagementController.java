package com.campusflow.auth;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;

@RestController @RequestMapping("/api/system/accounts")
public class AccountManagementController {
    private final AccountManagementService service;
    public AccountManagementController(AccountManagementService service){this.service=service;}
    @GetMapping public Map<String,Object> list(@Valid @ModelAttribute AccountQuery query){return service.list(query);}
    @GetMapping("/{id}") public AccountView get(@PathVariable long id){return service.get(id);}
    @PostMapping public AccountView create(Principal actor,@Valid @RequestBody AccountCreate input){return service.create(actor.getName(),input);}
    @PutMapping("/{id}") public AccountView update(Principal actor,@PathVariable long id,@Valid @RequestBody AccountUpdate input){return service.update(actor.getName(),id,input);}
}
