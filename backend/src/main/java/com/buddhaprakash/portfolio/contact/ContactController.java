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
 * Messages are emailed via Resend's HTTPS API when RESEND_API_KEY is set —
 * Render's free tier blocks outbound SMTP (ports 25/465/587), so SMTP relays
 * like Gmail are unreachable from this host; HTTPS on 443 always works.
 * Without the key, messages fall back to the application log (visible in
 * Render's log stream), so the endpoint keeps working unconfigured.
 * A simple fixed-window rate limit per IP guards against form spam;
 * ConcurrentHashMap suffices at portfolio traffic levels.
 */
@RestController
@RequestMapping("/api/contact")
@Tag(name = "Contact", description = "Contact form intake, rate-limited per IP")
public class ContactController {

    private static final Logger log = LoggerFactory.getLogger(ContactController.class);
    private static final int MAX_PER_HOUR = 5;

    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Instant>> hits = new ConcurrentHashMap<>();

    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final String resendApiKey;
    private final String mailTo;

    public ContactController(@org.springframework.beans.factory.annotation.Value("${portfolio-mail.resend-api-key}") String resendApiKey,
                             @org.springframework.beans.factory.annotation.Value("${portfolio-mail.to}") String mailTo) {
        this.resendApiKey = resendApiKey;
        this.mailTo = mailTo;
    }

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

        if (!resendApiKey.isBlank()) {
            try {
                String body = """
                        {"from":"Portfolio Contact <onboarding@resend.dev>",
                         "to":["%s"],
                         "reply_to":"%s",
                         "subject":"Portfolio contact: %s",
                         "text":"From: %s <%s>\\n\\n%s"}"""
                        .formatted(mailTo, jsonEscape(req.email()), jsonEscape(req.name()),
                                jsonEscape(req.name()), jsonEscape(req.email()), jsonEscape(req.message()));
                var request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("https://api.resend.com/emails"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + resendApiKey)
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                        .build();
                var response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 300) {
                    log.error("Resend API returned {}: {}", response.statusCode(), response.body());
                }
            } catch (Exception e) {
                // The message is already in the log above — never fail the visitor's
                // request because email delivery hiccuped.
                log.error("Failed to send contact email: {}", e.getMessage());
            }
        }
        return ResponseEntity.ok(Map.of("status", "received"));
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "").replace("\t", "\\t");
    }
}
