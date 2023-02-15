package dev.emortal.api.liveconfigparser;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);

    private final Set<WatchedConfig<?>> watchedConfigs = Collections.synchronizedSet(new HashSet<>());
    private final WatchService watchService = FileSystems.getDefault().newWatchService();

    public ConfigManager() throws IOException {
        new Thread(() -> {
            while (true) {
                try {
                    WatchKey key = this.watchService.take();
                    key.pollEvents().forEach(event -> {
                        Path path = (Path) event.context();
                        this.watchedConfigs.stream()
                                .filter(watchedConfig -> watchedConfig.getPath().equals(path))
                                .forEach(watchedConfig -> {
                                    watchedConfig.setLastUpdated(Instant.now());
                                    watchedConfig.getUpdateConsumer().accept(null);
                                });
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void watchConfig(@NotNull Path path) throws IOException {
        path.register(this.watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
    }

    private static class WatchedConfig<T> {
        private final @NotNull Path path;
        private final @NotNull Consumer<T> updateConsumer;

        private @NotNull Instant lastUpdated = Instant.now();

        private WatchedConfig(@NotNull Path path, @NotNull Consumer<T> updateConsumer) {
            this.path = path;
            this.updateConsumer = updateConsumer;
        }

        public @NotNull Path getPath() {
            return path;
        }

        public void setLastUpdated(@NotNull Instant lastUpdated) {
            this.lastUpdated = lastUpdated;
        }

        public @NotNull Instant getLastUpdated() {
            return lastUpdated;
        }

        public @NotNull Consumer<T> getUpdateConsumer() {
            return updateConsumer;
        }
    }
}
