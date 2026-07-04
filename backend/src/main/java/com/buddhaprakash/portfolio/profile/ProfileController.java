package com.buddhaprakash.portfolio.profile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Profile and project data live in a static JSON resource rather than a database:
 * the content changes only when the resume does, so a DB would add operational
 * cost (and a cold-start dependency on Render's free tier) for no benefit.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Profile", description = "Profile, projects and case-study content")
public class ProfileController {

    private String cached;

    @Operation(summary = "Full profile: skills, experience, projects, case studies")
    @GetMapping(value = "/profile", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> profile() throws IOException {
        if (cached == null) {
            cached = new String(
                    new ClassPathResource("profile.json").getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        }
        return ResponseEntity.ok(cached);
    }
}
