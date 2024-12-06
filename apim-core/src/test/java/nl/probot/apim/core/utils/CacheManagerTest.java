package nl.probot.apim.core.utils;

import nl.probot.apim.core.utils.CacheManager.TimedValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static io.quarkus.runtime.util.StringUtil.isNullOrEmpty;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
class CacheManagerTest {

    CacheManager cacheManager = new CacheManager();

    @BeforeAll
    public void init() {
        this.cacheManager.maxAmount = 100;
        this.cacheManager.keepTime = 300;
    }

    @AfterEach
    public void clean() {
        this.cacheManager.clearAll();
    }

    @NullSource
    @ValueSource(strings = "hello world")
    @ParameterizedTest
    public void addEntry(String value) {
        if (isNullOrEmpty(value)) {
            assertThatThrownBy(() -> this.cacheManager.get("test", () -> value)).isInstanceOf(NullPointerException.class);
            assertThat(this.cacheManager.cache).isEmpty();
        } else {
            var result = this.cacheManager.get("test", () -> "hello world");

            assertThat(result).isEqualTo("hello world");
            assertThat(this.cacheManager.cache).hasSize(1);
        }
    }

    @Test
    void maxEntry() {
        // 50 old entries
        for (int i = 1; i <= 50; i++) {
            this.cacheManager.cache.put("%d".formatted(i), new TimedValue(i, Instant.now().minus(6, MINUTES).toEpochMilli()));
        }

        // new entries
        for (int i = 51; i <= 100; i++) {
            this.cacheManager.cache.put("%d".formatted(i), new TimedValue(i));
        }

        var value = this.cacheManager.get("101", () -> "I should be present");
        assertThat(this.cacheManager.cache).hasSize(51).allSatisfy((k, v) -> assertThat(Integer.valueOf(k)).isGreaterThanOrEqualTo(51));
        assertThat(value).isEqualTo("I should be present");
    }
}