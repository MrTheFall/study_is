package com.example.orgmanager.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "app_lock")
@Getter
@Setter
public class AppLock {
    private static final int LOCK_NAME_MAX_LENGTH = 64;

    @Id
    @Column(name = "name", nullable = false, updatable = false, length = LOCK_NAME_MAX_LENGTH)
    private String name;
}
