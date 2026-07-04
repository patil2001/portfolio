package com.buddhaprakash.portfolio.contact;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Messages are logged (visible in Render's log stream) rather than emailed —
 * sending mail needs SMTP credentials in env config, which can be added later
 * without an API change. A simple fixed-window rate limit per IP guards against
 * form spam; ConcurrentHashMap suffices at portfolio traffic levels.
 */
@RestController
@RequestMapping("/api/contact")
@Tag(name = "Contact", description = "Contact form intake, rate-limited per IP")
public class ContactController {

    private static final Logger log = LoggerFactory.getLogger(ContactController.class);
    private static final int MAX_PER_HOUR = 5;

    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Instant>> hits = new ConcurrentHashMap<>();

    public record ContactRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Size(max = 2000) String message) {
    }

    @Operation(summary = "Submit a message (max 5/hour per IP)")
    @PostMapping
    public ResponseEntity<Map<String, String>> submit(@Valid @RequestBody ContactRequest req,
                                                      jakarta.servlet.http.HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        var window = hits.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        window.removeIf(t -> t.isBefore(cutoff));
        if (window.size() >= MAX_PER_HOUR) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "rate_limit_exceeded"));
        }
        window.add(Instant.now());

        log.info("CONTACT message from {} <{}>: {}", req.name(), req.email(), req.message());
        return ResponseEntity.ok(Map.of("status", "received"));
    }
}
