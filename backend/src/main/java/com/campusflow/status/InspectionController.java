package com.campusflow.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/data")
public class InspectionController {
    private final InspectionService inspection;
    public InspectionController(InspectionService inspection) { this.inspection = inspection; }
    @GetMapping("/records") Map<String,Object> records(@RequestParam InspectionService.Kind kind,
        @RequestParam(required=false) @Positive Long spaceId, @RequestParam(required=false) Boolean valid,
        @RequestParam(defaultValue="1") @Min(1) @Max(1000000) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return inspection.records(kind,spaceId,valid,page,pageSize);
    }
    @GetMapping("/devices") List<Map<String,Object>> devices() { return inspection.devices(); }
    public record ValidityInput(@NotNull Boolean valid, @NotBlank @Size(max=500) String reason) {}
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @PutMapping("/records/{kind}/{id}/validity") void validity(@PathVariable InspectionService.Kind kind, @PathVariable long id,
        @Valid @RequestBody ValidityInput input, Authentication actor) {
        inspection.validity(kind,id,input.valid(),input.reason(),actor.getName());
    }
}

