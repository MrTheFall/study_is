package com.example.orgmanager.config;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.jsr107.EhcacheCachingProvider;
import org.ehcache.jsr107.config.ConfigurationElementState;
import org.ehcache.jsr107.config.Jsr107Configuration;
import org.hibernate.cache.jcache.ConfigSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EhcacheConfig implements HibernatePropertiesCustomizer {
    private static final long CACHE_HEAP_ENTRIES = 5000L;
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    @Bean(destroyMethod = "close")
    public CacheManager cacheManager() {
        CachingProvider cachingProvider = Caching.getCachingProvider(
                "org.ehcache.jsr107.EhcacheCachingProvider");
        EhcacheCachingProvider ehcacheProvider =
                (EhcacheCachingProvider) cachingProvider;

        CacheConfiguration<Object, Object> cacheConfiguration =
                CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                Object.class,
                                Object.class,
                                ResourcePoolsBuilder.heap(CACHE_HEAP_ENTRIES))
                        .withExpiry(
                                ExpiryPolicyBuilder.timeToLiveExpiration(
                                        CACHE_TTL))
                        .build();

        Jsr107Configuration jsr107Configuration = new Jsr107Configuration(
                null,
                Map.of(),
                true,
                ConfigurationElementState.DISABLED,
                ConfigurationElementState.ENABLED);

        org.ehcache.config.Configuration configuration =
                ConfigurationBuilder.newConfigurationBuilder()
                        .withService(jsr107Configuration)
                        .withCache(
                                "com.example.orgmanager.model.Organization",
                                cacheConfiguration)
                        .withCache(
                                "default-query-results-region",
                                cacheConfiguration)
                        .withCache(
                                "default-update-timestamps-region",
                                cacheConfiguration)
                        .build();

        return ehcacheProvider.getCacheManager(
                ehcacheProvider.getDefaultURI(),
                configuration,
                new Properties());
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(
                ConfigSettings.CACHE_MANAGER,
                cacheManager());
    }
}
