package nl.probot.apim.core.utils;

import io.quarkus.logging.Log;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import nl.probot.apim.core.entities.SubscriptionEntity;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A very basic cache implementation for the APIM.
 * <p>
 * This prevents that the {@link SubscriptionEntity} is not looked up each time when forwarding requests to the downstream.
 */
@Singleton
public class CacheManager {

    @Min(10)
    @Max(500)
    @ConfigProperty(name = "apim.cache.size", defaultValue = "100")
    int maxAmount;

    @Min(60)
    @Max(3600)
    @ConfigProperty(name = "apim.cache.max.keep.time", defaultValue = "300")
    int keepTime;

    Map<String, TimedValue> cache = new ConcurrentHashMap<>(100);

    public <T> T getAndSet(String key, Supplier<T> supplier) {
        var timedValue = this.cache.get(key);
        if (timedValue == null) {
            var result = supplier.get();
            checkCacheSize();

            this.cache.put(key, new TimedValue(result));
            return result;
        }

        return (T) timedValue.value;
    }

    public Optional<TimedValue> get(String key) {
        return Optional.ofNullable(this.cache.get(key));
    }

    public void set(String key, Object value) {
        checkCacheSize();
        this.cache.put(key, new TimedValue(value));
    }

    public void invalidate(String key) {
        this.cache.remove(key);
    }

    public void clearAll() {
        this.cache.clear();
    }

    private void checkCacheSize() {
        var size = this.cache.size();

        if (size >= this.maxAmount) {
            Log.debugf("Removing stale date from cache, size: %d", size);
            this.cache.entrySet().removeIf(entry -> entry.getValue().isStale(this.keepTime));
        }
    }

    public record TimedValue(Object value, long timestamp) {

        public TimedValue(Object value) {
            this(Objects.requireNonNull(value), System.currentTimeMillis());
        }

        public boolean isStale(int keepTime) {
            return this.timestamp + keepTime < System.currentTimeMillis();
        }
    }
}
