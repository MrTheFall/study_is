package com.example.orgmanager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;

@Configuration
public class SchemaInitializer {
    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final DataSource dataSource;
    private final boolean enabled;

    public SchemaInitializer(DataSource dataSource,
                             @Value("${app.schema-init-enabled:true}") boolean enabled) {
        this.dataSource = dataSource;
        this.enabled = enabled;
    }

    @PostConstruct
    @Transactional
    public void init() {
        if (!enabled) {
            log.info("Schema initializer disabled by config");
            return;
        }
        try (Connection conn = dataSource.getConnection()) {
            var resource = new ClassPathResource("db/schema.sql");
            if (resource.exists()) {
                ScriptUtils.executeSqlScript(conn, resource);
                log.info("Schema ensured using db/schema.sql");
            } else {
                log.warn("db/schema.sql not found; skipping schema init");
            }
        } catch (Exception e) {
            log.error("Schema initialization failed", e);
            throw new RuntimeException(e);
        }
    }
}

