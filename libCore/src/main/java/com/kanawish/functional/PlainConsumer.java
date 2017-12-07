package com.kanawish.functional;

/**
 * A functional interface (callback) that accepts a single value.
 * @param <T> the value type
 */
@Deprecated // Should be possible to nuke once we moved to Kotlin
public interface PlainConsumer<T> {
    /**
     * Consume the given value.
     * @param t the value
     */
    void accept(T t);
}
