package com.example.orgmanager.service;

import java.util.Objects;
import java.util.Optional;

public record OrganizationFilter(Optional<String> field, Optional<String> value) {
    public OrganizationFilter {
        field = Objects.requireNonNull(field).map(String::trim).filter(f -> !f.isEmpty());
        value = Objects.requireNonNull(value).map(String::trim).filter(v -> !v.isEmpty());
    }

    public boolean isEmpty() {
        return field.isEmpty() || value.isEmpty();
    }
}
