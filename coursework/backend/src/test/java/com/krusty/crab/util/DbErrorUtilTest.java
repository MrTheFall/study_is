package com.krusty.crab.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DbErrorUtilTest {

    @Test
    void extractMeaningfulMessage_returnsNullForNullInput() {
        assertThat(DbErrorUtil.extractMeaningfulMessage(null)).isNull();
    }

    @Test
    void extractMeaningfulMessage_extractsErrorSubstring() {
        RuntimeException exception = new RuntimeException("ERROR: database down");

        assertThat(DbErrorUtil.extractMeaningfulMessage(exception)).isEqualTo("database down");
    }

    @Test
    void extractMeaningfulMessage_usesCauseWhenPresent() {
        RuntimeException exception =
                new RuntimeException("outer", new RuntimeException("ERROR: inner"));

        assertThat(DbErrorUtil.extractMeaningfulMessage(exception)).isEqualTo("inner");
    }

    @Test
    void extractMeaningfulMessage_returnsOriginalMessageWhenNoCause() {
        RuntimeException exception = new RuntimeException("plain");

        assertThat(DbErrorUtil.extractMeaningfulMessage(exception)).isEqualTo("plain");
    }
}
