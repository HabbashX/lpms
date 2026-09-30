package com.larv.pharmacy.settings;

import com.larv.pharmacy.audit.AuditAction;
import com.larv.pharmacy.audit.AuditService;
import com.larv.pharmacy.common.exception.InvalidRequestException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Administrator-managed pharmacy configuration. Values are cached in memory
 * and written through to the database.
 */
@Service
public class SettingsService {

    private final SystemSettingRepository repository;
    private final AuditService auditService;
    private final Clock clock;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SettingsService(SystemSettingRepository repository, AuditService auditService, Clock clock) {
        this.repository = repository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @PostConstruct
    public void loadDefaults() {
        List<SystemSetting> toSave = new ArrayList<>();
        for (SettingKey key : SettingKey.values()) {
            SystemSetting setting = repository.findById(key.getKey()).orElse(null);
            if (setting == null) {
                toSave.add(new SystemSetting(key.getKey(), key.getDefaultValue(), null, clock.instant()));
            } else {
                cache.put(key.getKey(), setting.getValue());
            }
        }
        if (!toSave.isEmpty()) {
            repository.saveAll(toSave);
            toSave.forEach(s -> cache.put(s.getKey(), s.getValue()));
        }
    }

    public boolean getBoolean(SettingKey key) {
        return Boolean.parseBoolean(get(key.getKey(), key.getDefaultValue()));
    }

    public int getInt(SettingKey key) {
        try {
            return Integer.parseInt(get(key.getKey(), key.getDefaultValue()));
        } catch (NumberFormatException e) {
            return Integer.parseInt(key.getDefaultValue());
        }
    }

    private String get(String key, String fallback) {
        String value = cache.get(key);
        return value == null ? fallback : value;
    }

    @Transactional(readOnly = true)
    public List<SettingResponse> getAll() {
        List<SettingResponse> responses = new ArrayList<>();
        for (SettingKey key : SettingKey.values()) {
            SystemSetting stored = repository.findById(key.getKey()).orElse(null);
            responses.add(new SettingResponse(
                    key.getKey(),
                    stored != null ? stored.getValue() : key.getDefaultValue(),
                    key.getDescription(),
                    key.getType().name(),
                    stored == null ? null : stored.getUpdatedBy(),
                    stored == null ? null : stored.getUpdatedAt()));
        }
        return responses;
    }

    @Transactional
    public List<SettingResponse> update(Map<String, String> settings) {
        if (settings == null || settings.isEmpty()) {
            throw new InvalidRequestException("EMPTY_SETTINGS", "Provide at least one setting to update");
        }
        Instant now = clock.instant();
        Long actor = AuditService.currentUserId().orElse(null);

        for (Map.Entry<String, String> entry : settings.entrySet()) {
            SettingKey definition = SettingKey.fromKey(entry.getKey());
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            validate(definition, value);

            SystemSetting setting = repository.findById(definition.getKey())
                    .orElseGet(() -> new SystemSetting(definition.getKey(), value, actor, now));
            setting.setValue(value);
            setting.setUpdatedBy(actor);
            setting.setUpdatedAt(now);
            repository.save(setting);
            cache.put(definition.getKey(), value);
        }

        auditService.record(AuditAction.UPDATE_SETTINGS, "Settings", null,
                "Updated settings: " + String.join(", ", settings.keySet()));
        return getAll();
    }

    private void validate(SettingKey definition, String value) {
        switch (definition.getType()) {
            case BOOLEAN -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new InvalidRequestException("INVALID_SETTING",
                            "Setting '" + definition.getKey() + "' must be 'true' or 'false'");
                }
            }
            case INTEGER -> {
                try {
                    int parsed = Integer.parseInt(value);
                    if (parsed < 0) {
                        throw new NumberFormatException("negative");
                    }
                } catch (NumberFormatException e) {
                    throw new InvalidRequestException("INVALID_SETTING",
                            "Setting '" + definition.getKey() + "' must be a non-negative integer");
                }
            }
        }
    }
}
