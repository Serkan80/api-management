package nl.probot.api.management.utils;

import io.quarkus.logging.Log;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Singleton
public class CacheManager {

    static final int MAX_AMOUNT = 100;
    static final int OLD_DATA_CLEANUP_MILLIS = 300_000;
    Map<String, TimedValue> cache = new ConcurrentHashMap<>(MAX_AMOUNT);

    public <T> T get(String key, Supplier<T> supplier) {
        var timedValue = this.cache.get(key);
        if (timedValue == null) {
            var result = supplier.get();
            var size = cache.size();

            if (size >= MAX_AMOUNT) {
                Log.debugf("Removing stale date from cache, size: %d", size);
                this.cache.entrySet().removeIf(entry -> entry.getValue().isStale());
            }

            Log.debugf("Cache miss: %s", key);
            this.cache.put(key, new TimedValue(result));
            return result;
        }

        Log.debugf("Cache hit: %s", key);
        return (T) timedValue.value;
    }

    public void update(String key, Object value) {
        this.cache.put(key, new TimedValue(value));
        Log.debugf("Cache update for key %s", key);
    }

    record TimedValue(Object value, long timestamp) {

        public TimedValue(Object value) {
            this(value, System.currentTimeMillis());
        }

        public boolean isStale() {
            return this.timestamp + OLD_DATA_CLEANUP_MILLIS < System.currentTimeMillis();
        }
    }
}
