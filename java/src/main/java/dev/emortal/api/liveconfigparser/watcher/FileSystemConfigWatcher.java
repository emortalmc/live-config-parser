package dev.emortal.api.liveconfigparser.watcher;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class FileSystemConfigWatcher implements ConfigWatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemConfigWatcher.class);

    private final Path basePath;
    private final ConfigWatcherConsumer consumer;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final WatchService watchService;

    public FileSystemConfigWatcher(@NotNull Path path, @NotNull ConfigWatcherConsumer consumer) throws IOException {
        if (Files.notExists(path)) {
            throw new IllegalStateException("%s folder not found".formatted(path.toAbsolutePath()));
        }

        this.consumer = consumer;
        this.basePath = path;

        LOGGER.info("Watching config changes in %s".formatted(path.toAbsolutePath()));
        this.watchService = this.basePath.getFileSystem().newWatchService();
        path.register(this.watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);

        // Fire create events for all existing configs
        this.loadAllConfigs();

        this.listenForUpdates();
    }

    private void loadAllConfigs() throws IOException {
        try (Stream<Path> stream = Files.list(this.basePath)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            String contents = Files.readString(path);
                            this.consumer.onConfigCreate(path.getFileName().toString(), contents);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private void listenForUpdates() {
        this.executor.scheduleAtFixedRate(() -> {
            try {
                WatchKey key = this.watchService.poll();
                if (key == null) return;
                List<WatchEvent<?>> events = key.pollEvents();

                for (WatchEvent<?> event : events) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path path = this.basePath.resolve(pathEvent.context());
                    if (!path.getFileName().toString().endsWith(".json")) {
                        LOGGER.warn("Non-json file was modified: " + path);
                        continue;
                    }

                    switch (kind.name()) {
                        case "ENTRY_CREATE" -> {
                            String fileContents = Files.readString(path);
                            this.consumer.onConfigCreate(path.getFileName().toString(), fileContents);
                        }
                        case "ENTRY_MODIFY" -> {
                            String fileContents = Files.readString(path);
                            this.consumer.onConfigModify(path.getFileName().toString(), fileContents);
                        }
                        case "ENTRY_DELETE" -> this.consumer.onConfigDelete(path.getFileName().toString());
                    }
                }
                key.reset();
            } catch (Exception exception) {
                LOGGER.error("Error while trying to dispatch update", exception);
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() throws IOException {
        this.executor.shutdown();
        this.watchService.close();
    }
}
