package com.larv.pharmacy.settings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings")
@Tag(name = "Settings", description = "Pharmacy configuration (ADMIN only)")
@PreAuthorize("hasRole('ADMIN')")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    @Operation(summary = "List all pharmacy settings")
    public List<SettingResponse> getAll() {
        return settingsService.getAll();
    }

    @PutMapping
    @Operation(summary = "Update pharmacy settings",
            description = "Example body: {\"settings\": {\"inventory.allow_expired_sales\": \"true\"}}")
    public List<SettingResponse> update(@Valid @RequestBody UpdateSettingsRequest request) {
        return settingsService.update(request.settings());
    }

    public record UpdateSettingsRequest(Map<String, String> settings) {
    }
}
