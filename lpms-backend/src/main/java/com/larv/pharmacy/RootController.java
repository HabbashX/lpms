package com.larv.pharmacy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Application root. Used by platform health checks (e.g. Render probes "/")
 * that expect an unauthenticated 200 response.
 */
@RestController
@Tag(name = "Health")
public class RootController {

    @GetMapping("/")
    @Operation(summary = "Service root", description = "Unauthenticated liveness endpoint for platform health checks.")
    public Map<String, String> root() {
        return Map.of(
                "name", "lpms-backend",
                "status", "UP");
    }
}
