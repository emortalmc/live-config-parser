package dev.emortal.api.liveconfigparser.configs.gamemode;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import dev.emortal.api.liveconfigparser.adapter.DurationAdapter;
import dev.emortal.api.liveconfigparser.configs.ConfigUpdate;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameModeCollection {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameModeCollection.class);
    public static final Path FOLDER_PATH = Path.of("./config/gamemodes");

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Duration.class, new DurationAdapter())
            .create();

    private final WatchService watchService = FOLDER_PATH.getFileSystem().newWatchService();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private final Map<String, GameModeConfig> configs = new HashMap<>();
    private final Map<String, List<Consumer<ConfigUpdate<GameModeConfig>>>> updateListeners = new ConcurrentHashMap<>();
    private final List<Consumer<ConfigUpdate<GameModeConfig>>> globalListeners = new ArrayList<>();

    public GameModeCollection() throws IOException {
        if (Files.notExists(FOLDER_PATH)) {
            throw new IllegalStateException("%s folder not found".formatted(FOLDER_PATH.toAbsolutePath()));
        }
        FOLDER_PATH.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);

        List<GameModeConfig> allConfigs = this.loadAllConfigs();
        for (GameModeConfig config : allConfigs) {
            this.configs.put(config.getId(), config);
        }

        this.listenForUpdates();

        this.globalListeners.add(update -> {
            GameModeConfig config = update.getConfig();

            switch (update.getType()) {
                case CREATE -> this.configs.put(config.getId(), config);
                case DELETE -> this.configs.remove(update.getConfigId());
                case MODIFY -> {
                    GameModeConfig oldConfig = this.configs.get(config.getId());

                    if (!oldConfig.getId().equals(config.getId())) this.configs.remove(oldConfig.getId());
                    this.configs.put(config.getId(), config);
                }
            }
        });
    }

    private List<GameModeConfig> loadAllConfigs() throws IOException {
        try (Stream<Path> stream = Files.list(FOLDER_PATH)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> {
                        try {
                            return this.loadConfig(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());
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

                    ConfigUpdate.Type updateType = switch (kind.name()) {
                        case "ENTRY_DELETE" -> ConfigUpdate.Type.DELETE;
                        case "ENTRY_CREATE" -> ConfigUpdate.Type.CREATE;
                        case "ENTRY_MODIFY" -> ConfigUpdate.Type.MODIFY;
                        default -> throw new IllegalStateException("Unexpected watch event type: " + kind);
                    };

                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    final Path path = pathEvent.context();
                    if (!path.getFileName().toString().endsWith(".json")) {
                        LOGGER.warn("Non-json file was modified: " + path);
                        continue;
                    }

                    final Optional<GameModeConfig> optionalOldConfig = this.configs.values().stream()
                            .filter(config -> config.getPath().equals(path))
                            .findFirst();

                    final GameModeConfig config;
                    if (updateType != ConfigUpdate.Type.DELETE) {
                        try {
                            config = this.loadConfig(path);
                        } catch (IOException e) {
                            LOGGER.error("Failed to read config file: " + path, e);
                            return;
                        }
                    } else {
                        config = null;
                    }

                    final String id = optionalOldConfig.isPresent() ? optionalOldConfig.get().getId() : config.getId();

                    ConfigUpdate<GameModeConfig> update = new ConfigUpdate<>(id, config, updateType);
                    if (this.updateListeners.containsKey(id))
                        this.updateListeners.get(id).forEach(listener -> listener.accept(update));
                    this.globalListeners.forEach(listener -> listener.accept(update));
                }
                key.reset();
            } catch (final Exception exception) {
                LOGGER.error("Error while trying to dispatch update", exception);
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    private @NotNull GameModeConfig loadConfig(@NotNull Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            GameModeConfig config = GSON.fromJson(reader, GameModeConfig.class);
            config.setPath(path);
            return config;
        }
    }

    /**
     * @param id             the id of the config to get
     * @param updateListener a listener that will be called when the config is deleted or modified
     * @return the config
     */
    public @NotNull GameModeConfig getConfig(String id, Consumer<ConfigUpdate<GameModeConfig>> updateListener) throws IllegalArgumentException {
        GameModeConfig config = this.configs.get(id);
        if (config == null) {
            throw new IllegalArgumentException("Config %s not found".formatted(id));
        }

        this.updateListeners.computeIfAbsent(id, k -> new ArrayList<>()).add(updateListener);
        return config;
    }

    /**
     * @param updateListener a listener that will be called when a config is created, deleted or modified
     * @return a copy of the original configs
     */
    public List<GameModeConfig> getAllConfigs(Consumer<ConfigUpdate<GameModeConfig>> updateListener) {
        List<GameModeConfig> configs = new ArrayList<>(this.configs.values());
        this.globalListeners.add(updateListener);

        return configs;
    }
}
