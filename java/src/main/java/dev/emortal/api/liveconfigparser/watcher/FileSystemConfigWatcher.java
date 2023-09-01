package dev.emortal.api.liveconfigparser.watcher;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
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

public final class FileSystemConfigWatcher implements ConfigWatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemConfigWatcher.class);

    private final @NotNull Path watchedFolder;
    private final @NotNull ConfigWatcherConsumer consumer;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final WatchService watchService;

    public FileSystemConfigWatcher(@NotNull Path path, @NotNull ConfigWatcherConsumer consumer) throws IOException {
        if (Files.notExists(path)) {
            throw new IllegalStateException("%s folder not found".formatted(path.toAbsolutePath()));
        }

        this.consumer = consumer;
        this.watchedFolder = path;

        LOGGER.info("Watching config changes in '{}'", path.toAbsolutePath());
        this.watchService = this.watchedFolder.getFileSystem().newWatchService();
        path.register(this.watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);

        // Fire create events for all existing configs
        this.loadAllConfigs();

        this.startUpdateListener();
    }

    private void loadAllConfigs() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(this.watchedFolder, this::isConfigFile)) {
            // We use a directory stream so that we can iterate in an imperative way, to be able to propagate the IOException
            for (Path path : stream) {
                String contents = Files.readString(path);
                this.consumer.onConfigCreate(path.getFileName().toString(), contents);
            }
        }
    }

    private boolean isConfigFile(@NotNull Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json");
    }

    private void startUpdateListener() {
        this.scheduler.scheduleAtFixedRate(this::pollWatchEvents, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void pollWatchEvents() {
        try {
            WatchKey key = this.watchService.poll();
            if (key == null) return; // No events received for us to process

            List<WatchEvent<?>> events = key.pollEvents();
            for (WatchEvent<?> event : events) {
                @SuppressWarnings("unchecked") // This is fine, as the watch service was created from a path and is only watching paths
                WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;

                this.processEvent(pathEvent);
            }

            key.reset();
        } catch (IOException exception) {
            LOGGER.error("Failed to dispatch config file update", exception);
        }
    }

    private void processEvent(@NotNull WatchEvent<Path> event) throws IOException {
        WatchEvent.Kind<?> kind = event.kind();
        if (kind == StandardWatchEventKinds.OVERFLOW) return;

        Path path = this.watchedFolder.resolve(event.context());
        if (!path.getFileName().toString().endsWith(".json")) {
            LOGGER.warn("Non-json file '{}' in config directory was modified", path);
            return;
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

    @Override
    public void close() throws IOException {
        this.scheduler.shutdown();
        this.watchService.close();
    }
}
