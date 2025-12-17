package com.example.orgmanager.service.cache;

import jakarta.persistence.EntityManagerFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Aspect
@Component
@ConditionalOnProperty(prefix = "app.jpa", name = "cache-stats-logging-enabled", havingValue = "true")
public class CacheStatisticsLoggingAspect {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheStatisticsLoggingAspect.class);
    private final Statistics statistics;

    private final ThreadLocal<Long> hitsBeforeHolder = new ThreadLocal<>();
    private final ThreadLocal<Long> missesBeforeHolder = new ThreadLocal<>();

    public CacheStatisticsLoggingAspect(EntityManagerFactory entityManagerFactory) {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        this.statistics = sessionFactory.getStatistics();
        this.statistics.setStatisticsEnabled(true);
    }

    @Before("execution(* com.example.orgmanager.repository..*(..))")
    public void captureStatsBefore() {
        hitsBeforeHolder.set(statistics.getSecondLevelCacheHitCount());
        missesBeforeHolder.set(statistics.getSecondLevelCacheMissCount());
    }

    @After("execution(* com.example.orgmanager.repository..*(..))")
    public void logStatsAfter(JoinPoint joinPoint) {
        Long hitsBefore = hitsBeforeHolder.get();
        Long missesBefore = missesBeforeHolder.get();

        if (hitsBefore == null || missesBefore == null) {
            return;
        }

        hitsBeforeHolder.remove();
        missesBeforeHolder.remove();

        long hitsAfter = statistics.getSecondLevelCacheHitCount();
        long missesAfter = statistics.getSecondLevelCacheMissCount();

        long hitDelta = hitsAfter - hitsBefore;
        long missDelta = missesAfter - missesBefore;

        if (hitDelta != 0 || missDelta != 0) {
            LOGGER.info(
                    "L2 cache stats [{}]: hits {} ({}), misses {} ({})",
                    joinPoint.getSignature().toShortString(),
                    hitsAfter,
                    formatDelta(hitDelta),
                    missesAfter,
                    formatDelta(missDelta));
        }
    }

    private String formatDelta(long delta) {
        return delta > 0 ? "+" + delta : String.valueOf(delta);
    }
}