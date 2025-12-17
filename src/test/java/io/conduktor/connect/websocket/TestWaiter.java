package io.conduktor.connect.websocket;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Deterministic waiting utility for tests.
 * Addresses SME review finding: "HIGH: Thread.sleep() timing assumptions cause flakiness"
 *
 * Replaces Thread.sleep() with condition-based waiting that:
 * - Polls condition at regular intervals
 * - Fails fast when condition is met
 * - Provides clear timeout messages
 * - Reduces test flakiness from timing assumptions
 */
public class TestWaiter {

    private static final long DEFAULT_POLL_INTERVAL_MS = 50;
    private static final long DEFAULT_TIMEOUT_MS = 5000;

    /**
     * Wait until a condition becomes true or timeout occurs
     *
     * @param condition the condition to wait for
     * @param timeoutMs maximum time to wait in milliseconds
     * @param message   error message if timeout occurs
     * @throws AssertionError if timeout occurs before condition is met
     */
    public static void waitUntil(BooleanSupplier condition, long timeoutMs, String message) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            sleep(DEFAULT_POLL_INTERVAL_MS);
        }
        if (!condition.getAsBoolean()) {
            throw new AssertionError(message + " (timeout after " + timeoutMs + "ms)");
        }
    }

    /**
     * Wait until a condition becomes true with default timeout
     *
     * @param condition the condition to wait for
     * @param message   error message if timeout occurs
     */
    public static void waitUntil(BooleanSupplier condition, String message) {
        waitUntil(condition, DEFAULT_TIMEOUT_MS, message);
    }

    /**
     * Wait until a supplier returns a non-null value or timeout occurs
     *
     * @param supplier  the supplier to check
     * @param timeoutMs maximum time to wait in milliseconds
     * @param message   error message if timeout occurs
     * @return the non-null value from supplier
     * @throws AssertionError if timeout occurs before getting non-null value
     */
    public static <T> T waitForNonNull(Supplier<T> supplier, long timeoutMs, String message) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        T result = null;
        while (result == null && System.currentTimeMillis() < deadline) {
            try {
                result = supplier.get();
            } catch (RuntimeException e) {
                // Re-throw runtime exceptions
                throw e;
            }
            if (result == null) {
                sleep(DEFAULT_POLL_INTERVAL_MS);
            }
        }
        if (result == null) {
            throw new AssertionError(message + " (timeout after " + timeoutMs + "ms)");
        }
        return result;
    }

    /**
     * Wait until a supplier returns a non-null value with default timeout
     *
     * @param supplier the supplier to check
     * @param message  error message if timeout occurs
     * @return the non-null value from supplier
     */
    public static <T> T waitForNonNull(Supplier<T> supplier, String message) {
        return waitForNonNull(supplier, DEFAULT_TIMEOUT_MS, message);
    }

    /**
     * Functional interface for suppliers that may throw checked exceptions
     */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    /**
     * Wait until a throwing supplier returns a non-null value or timeout occurs
     *
     * @param supplier  the supplier to check
     * @param timeoutMs maximum time to wait in milliseconds
     * @param message   error message if timeout occurs
     * @return the non-null value from supplier
     * @throws AssertionError if timeout occurs before getting non-null value
     */
    public static <T> T waitForNonNull(ThrowingSupplier<T> supplier, long timeoutMs, String message) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        T result = null;
        while (result == null && System.currentTimeMillis() < deadline) {
            try {
                result = supplier.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting", e);
            } catch (Exception e) {
                throw new RuntimeException("Unexpected exception while waiting", e);
            }
            if (result == null) {
                sleep(DEFAULT_POLL_INTERVAL_MS);
            }
        }
        if (result == null) {
            throw new AssertionError(message + " (timeout after " + timeoutMs + "ms)");
        }
        return result;
    }

    /**
     * Wait until a throwing supplier returns a non-null value with default timeout
     *
     * @param supplier the supplier to check
     * @param message  error message if timeout occurs
     * @return the non-null value from supplier
     */
    public static <T> T waitForNonNull(ThrowingSupplier<T> supplier, String message) {
        return waitForNonNull(supplier, DEFAULT_TIMEOUT_MS, message);
    }

    /**
     * Wait for a specific duration (use sparingly, prefer condition-based waiting)
     *
     * @param duration the duration to wait
     */
    public static void waitFor(Duration duration) {
        sleep(duration.toMillis());
    }

    /**
     * Wait for a specific duration in milliseconds (use sparingly, prefer condition-based waiting)
     *
     * @param millis milliseconds to wait
     */
    public static void waitFor(long millis) {
        sleep(millis);
    }

    /**
     * Poll a condition and return true if it becomes true within timeout, false otherwise
     * Does not throw exception on timeout - useful for optional conditions
     *
     * @param condition the condition to check
     * @param timeoutMs maximum time to wait
     * @return true if condition met within timeout, false otherwise
     */
    public static boolean eventually(BooleanSupplier condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            sleep(DEFAULT_POLL_INTERVAL_MS);
        }
        return condition.getAsBoolean();
    }

    /**
     * Poll a condition with default timeout
     *
     * @param condition the condition to check
     * @return true if condition met within default timeout, false otherwise
     */
    public static boolean eventually(BooleanSupplier condition) {
        return eventually(condition, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Wait for WebSocket connection to be established
     *
     * @param server    the mock WebSocket server
     * @param timeoutMs maximum time to wait
     */
    public static void waitForConnection(MockWebSocketServer server, long timeoutMs) {
        waitUntil(server::hasActiveConnection, timeoutMs, "Connection should be established");
    }

    /**
     * Wait for a message to be received by server
     *
     * @param server  the mock WebSocket server
     * @param timeout maximum time to wait
     * @param unit    time unit
     * @return the received message
     */
    public static String waitForMessage(MockWebSocketServer server, long timeout, TimeUnit unit) {
        try {
            String message = server.waitForMessage(timeout, unit);
            if (message == null) {
                throw new AssertionError("Expected to receive message within " + timeout + " " + unit);
            }
            return message;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for message", e);
        }
    }

    /**
     * Internal sleep helper that handles interrupts
     */
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting", e);
        }
    }
}
