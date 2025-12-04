package com.example.orgmanager.service.cache;

import jakarta.persistence.EntityManagerFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Aspect
@Component
@ConditionalOnProperty(
        prefix = "app.jpa",
        name = "cache-stats-logging-enabled",
        havingValue = "true")
public class CacheStatisticsLoggingAspect {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(CacheStatisticsLoggingAspect.class);

    private final Statistics statistics;

    public CacheStatisticsLoggingAspect(
            EntityManagerFactory entityManagerFactory) {
        SessionFactory sessionFactory = entityManagerFactory
                .unwrap(SessionFactory.class);
        this.statistics = sessionFactory.getStatistics();
        this.statistics.setStatisticsEnabled(true);
    }

    @Around("execution(* com.example.orgmanager.repository..*(..))")
    public Object logSecondLevelCacheStats(
            ProceedingJoinPoint joinPoint) throws Throwable {
        long hitsBefore = statistics.getSecondLevelCacheHitCount();
        long missesBefore = statistics.getSecondLevelCacheMissCount();
        try {
            return joinPoint.proceed();
        } finally {
            long hitDelta = statistics.getSecondLevelCacheHitCount()
                    - hitsBefore;
            long missDelta = statistics.getSecondLevelCacheMissCount()
                    - missesBefore;
            if (hitDelta != 0 || missDelta != 0) {
                LOGGER.info(
                        "L2 cache stats [{}]: hits {} ({}), misses {} ({})",
                        joinPoint.getSignature().toShortString(),
                        statistics.getSecondLevelCacheHitCount(),
                        formatDelta(hitDelta),
                        statistics.getSecondLevelCacheMissCount(),
                        formatDelta(missDelta));
            }
        }
    }

    private String formatDelta(long delta) {
        return delta > 0 ? "+" + delta : String.valueOf(delta);
    }
}
