package dev.emortal.api.liveconfigparser.watcher;

import org.jetbrains.annotations.NotNull;

public interface ConfigWatcherConsumer {

    void onConfigCreate(@NotNull String fileName, @NotNull String fileContents);

    void onConfigModify(@NotNull String fileName, @NotNull String fileContents);

    void onConfigDelete(@NotNull String fileName);
}
