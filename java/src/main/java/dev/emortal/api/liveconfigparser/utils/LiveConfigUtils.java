package dev.emortal.api.liveconfigparser.utils;

import org.jetbrains.annotations.NotNull;

public class LiveConfigUtils {

    public static @NotNull Thread createThread(@NotNull String configId, @NotNull Runnable runnable) {
        return new Thread(runnable, "LiveConfigParser-" + configId);
    }
}
