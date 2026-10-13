package com.nexgate.util;

/**
 * Time utility methods.
 */
public class TimeUtils {
    private TimeUtils() {
    }
    
    public static long now() {
        return System.currentTimeMillis();
    }
    
    public static boolean isExpired(long timestamp, long maxAgeMillis) {
        return Math.abs(now() - timestamp) > maxAgeMillis;
    }
}
