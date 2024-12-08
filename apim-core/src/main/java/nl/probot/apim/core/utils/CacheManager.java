package nl.probot.apim.core.utils;

import io.quarkus.logging.Log;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import nl.probot.apim.core.entities.SubscriptionEntity;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A very basic cache implementation for the APIM.
 * <p>
 * This prevents that the {@link SubscriptionEntity} is not looked up each time to forward requests to the downstream.
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

    public <T> T get(String key, Supplier<T> supplier) {
        var timedValue = this.cache.get(key);
        if (timedValue == null) {
            var result = supplier.get();
            var size = this.cache.size();

            if (size >= this.maxAmount) {
                Log.debugf("Removing stale date from cache, size: %d", size);
                this.cache.entrySet().removeIf(entry -> entry.getValue().isStale(this.keepTime));
            }

            Log.trace("Cache miss");
            this.cache.put(key, new TimedValue(result));
            return result;
        }

        Log.trace("Cache hit");
        return (T) timedValue.value;
    }

    public void invalidate(String key) {
        this.cache.remove(key);
    }

    public void clearAll() {
        this.cache.clear();
        Log.trace("Cache cleared");
    }

    record TimedValue(Object value, long timestamp) {

        public TimedValue(Object value) {
            this(Objects.requireNonNull(value), System.currentTimeMillis());
        }

        public boolean isStale(int keepTime) {
            return this.timestamp + keepTime < System.currentTimeMillis();
        }
    }
}
