package com.nexgate.crypto;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production-grade Argon2id password hashing service with strict concurrency bounds.
 * Prevents JVM OutOfMemoryError and CPU starvation by limiting concurrent memory-hard operations via a Semaphore.
 */
public class Argon2Service {
    private static final Logger logger = LoggerFactory.getLogger(Argon2Service.class);

    // Matches valid Argon2id encoded hashes: $argon2id$v=19$m=65536,t=3,p=1$...
    private static final Pattern ARGON2_PATTERN = Pattern.compile(
            "^\\$argon2id\\$v=19\\$m=(\\d+),t=(\\d+),p=(\\d+)\\$[A-Za-z0-9+/=]+\\$[A-Za-z0-9+/=]+$"
    );

    private final Argon2 argon2;
    private final Semaphore semaphore;
    private final ExecutorService executorService;
    private final int iterations;
    private final int memoryKb;
    private final int parallelism;

    public Argon2Service(int maxConcurrent, int iterations, int memoryKb, int parallelism) {
        this.argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
        this.semaphore = new Semaphore(Math.max(1, maxConcurrent), true);
        this.iterations = iterations > 0 ? iterations : 3;
        this.memoryKb = memoryKb >= 1024 ? memoryKb : 65536;
        this.parallelism = parallelism > 0 ? parallelism : 1;

        this.executorService = new ThreadPoolExecutor(
                1, Math.max(2, maxConcurrent * 2),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(250),
                new ThreadFactory() {
                    private int count = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "Argon2-Worker-" + (++count));
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public static Argon2Service createDefault() {
        return new Argon2Service(2, 3, 65536, 1);
    }

    /**
     * Hashes a password asynchronously with concurrency throttling.
     */
    public CompletableFuture<String> hashPasswordAsync(String password) {
        if (password == null || password.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Password cannot be null or empty"));
        }
        return CompletableFuture.supplyAsync(() -> hashPassword(password), executorService);
    }

    /**
     * Verifies a password against an encoded Argon2id hash asynchronously with concurrency throttling.
     */
    public CompletableFuture<Boolean> verifyPasswordAsync(String password, String hash) {
        if (password == null || hash == null || hash.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> verifyPassword(password, hash), executorService);
    }

    /**
     * Synchronous password hashing with semaphore acquisition.
     */
    public String hashPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        boolean acquired = false;
        try {
            // Block up to 5 seconds waiting for a hashing slot
            acquired = semaphore.tryAcquire(5, TimeUnit.SECONDS);
            if (!acquired) {
                logger.warn("Argon2 hashing throttled: server is under high authentication load!");
                throw new IllegalStateException("Authentication service is temporarily busy. Please retry.");
            }
            return argon2.hash(iterations, memoryKb, parallelism, password.toCharArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Password hashing interrupted", e);
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    /**
     * Synchronous password verification with format validation and semaphore acquisition.
     */
    public boolean verifyPassword(String password, String hash) {
        if (password == null || hash == null || hash.isEmpty()) {
            return false;
        }

        // Validate hash format before passing to native library
        if (!isValidArgon2Hash(hash)) {
            logger.warn("Rejected invalid password hash format: {}", hash.substring(0, Math.min(20, hash.length())));
            return false;
        }

        boolean acquired = false;
        try {
            acquired = semaphore.tryAcquire(5, TimeUnit.SECONDS);
            if (!acquired) {
                logger.warn("Argon2 verification throttled: server is under high load!");
                return false;
            }
            return argon2.verify(hash, password.toCharArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            logger.warn("Argon2 verification exception: {}", e.getMessage());
            return false;
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    /**
     * Checks if a hash complies with the canonical Argon2id format.
     */
    public boolean isValidArgon2Hash(String hash) {
        if (hash == null) return false;
        return ARGON2_PATTERN.matcher(hash).matches();
    }

    /**
     * Checks if a password hash was generated with older/weaker parameters and should be rehashed.
     */
    public boolean needsRehash(String hash) {
        if (hash == null) return true;
        Matcher matcher = ARGON2_PATTERN.matcher(hash);
        if (!matcher.matches()) {
            return true;
        }
        try {
            int m = Integer.parseInt(matcher.group(1));
            int t = Integer.parseInt(matcher.group(2));
            int p = Integer.parseInt(matcher.group(3));
            return m < memoryKb || t < iterations || p < parallelism;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    public void close() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
