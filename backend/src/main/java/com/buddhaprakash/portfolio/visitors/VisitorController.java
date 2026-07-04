package com.buddhaprakash.portfolio.visitors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory counter — resets on redeploy/cold start, which is acceptable for a
 * portfolio widget. Documented tradeoff: persistence would need Redis or a DB,
 * neither of which is worth a paid tier here. AtomicLong (not volatile long++)
 * because increments from concurrent requests must not lose updates.
 */
@RestController
@RequestMapping("/api/visitors")
@Tag(name = "Visitors", description = "Lightweight visit counter")
public class VisitorController {

    private final AtomicLong count = new AtomicLong();

    @Operation(summary = "Record a visit and return the running total")
    @PostMapping
    public Map<String, Long> increment() {
        return Map.of("visits", count.incrementAndGet());
    }

    @Operation(summary = "Current visit count")
    @GetMapping
    public Map<String, Long> current() {
        return Map.of("visits", count.get());
    }
}
