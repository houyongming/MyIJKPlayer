package com.linglongtech.ijklib.utils;

import java.util.Locale;

/**
 * Created by admin on 2018/9/17.
 */

public class PlayUtils {
    public static String formatedSpeed(long bytes, long elapsed_milli) {
        if (elapsed_milli <= 0) {
            return "0 B/s";
        }

        if (bytes <= 0) {
            return "0 B/s";
        }

        float bytes_per_sec = ((float) bytes) * 1000.f / elapsed_milli;
        if (bytes_per_sec >= 1024 * 1024) {
            return String.format(Locale.US, "%.2f MB/s", ((float) bytes_per_sec) / 1024 / 1024);
        } else if (bytes_per_sec >= 1024) {
            return String.format(Locale.US, "%.1f KB/s", ((float) bytes_per_sec) / 1024);
        } else {
            return String.format(Locale.US, "%d B/s", (long) bytes_per_sec);
        }
    }
}
