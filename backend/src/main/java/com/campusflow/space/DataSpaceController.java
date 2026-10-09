package com.campusflow.space;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/data/spaces")
public class DataSpaceController {
    private final SpaceService spaces;
    public DataSpaceController(SpaceService spaces) { this.spaces = spaces; }
    @GetMapping List<StudySpace> all() { return spaces.all(); }
    @PostMapping StudySpace create(@Valid @RequestBody SpaceInput input, Authentication actor) {
        return spaces.save(null, input, actor.getName());
    }
    @PutMapping("/{id}") StudySpace update(@PathVariable long id, @Valid @RequestBody SpaceInput input, Authentication actor) {
        return spaces.save(id, input, actor.getName());
    }
}
