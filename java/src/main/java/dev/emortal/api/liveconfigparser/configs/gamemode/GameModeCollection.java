package dev.emortal.api.liveconfigparser.configs.gamemode;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import dev.emortal.api.liveconfigparser.adapter.DurationAdapter;
import dev.emortal.api.liveconfigparser.configs.ConfigUpdate;
import dev.emortal.api.liveconfigparser.utils.LiveConfigUtils;

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

public class GameModeCollection {
    public static final Path FOLDER_PATH = Path.of("./config/gamemodes");

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Duration.class, new DurationAdapter())
            .create();

    private final WatchService watchService = FOLDER_PATH.getFileSystem().newWatchService();

    private final Map<String, GameModeConfig> configs = new HashMap<>();
    private final Map<String, List<Consumer<ConfigUpdate<GameModeConfig>>>> updateListeners = new ConcurrentHashMap<>();
    private final List<Consumer<ConfigUpdate<GameModeConfig>>> createListeners = new ArrayList<>();

    public GameModeCollection() throws IOException {
        if (Files.notExists(FOLDER_PATH)) {
            throw new IllegalStateException("%s folder not found".formatted(FOLDER_PATH.toAbsolutePath()));
        }

        List<GameModeConfig> allConfigs = this.loadAllConfigs();
        for (GameModeConfig config : allConfigs) {
            this.configs.put(config.getId(), config);
        }

        this.listenForUpdates();
    }

    private List<GameModeConfig> loadAllConfigs() throws IOException {
        try (Stream<Path> stream = Files.list(FOLDER_PATH)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> {
                        try {
                            return (GameModeConfig) GSON.fromJson(new JsonReader(Files.newBufferedReader(path)), GameModeConfig.class);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());
        }
    }

    private void listenForUpdates() {
        LiveConfigUtils.createThread("gamemodes", () -> {
            while (true) {
                try {
                    WatchKey key = this.watchService.take();
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

                        GameModeConfig config;

                        try (BufferedReader reader = Files.newBufferedReader(FOLDER_PATH.resolve(pathEvent.context()))) {
                            config = GSON.fromJson(reader, GameModeConfig.class);
                        } catch (IOException e) {
                            e.printStackTrace();
                            return;
                        }

                        ConfigUpdate<GameModeConfig> update = new ConfigUpdate<>(config.getId(), config, updateType);
                        this.updateListeners.get(config.getId()).forEach(listener -> listener.accept(update));
                        if (updateType == ConfigUpdate.Type.CREATE) {
                            this.createListeners.forEach(listener -> listener.accept(update));
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public GameModeConfig getConfig(String id, Consumer<ConfigUpdate<GameModeConfig>> updateListener) {
        GameModeConfig config = this.configs.get(id);
        if (config == null) {
            throw new IllegalStateException("Config %s not found".formatted(id));
        }

        this.updateListeners.computeIfAbsent(id, k -> new ArrayList<>()).add(updateListener);
        return config;
    }

    public List<GameModeConfig> getAllConfigs(Consumer<ConfigUpdate<GameModeConfig>> updateListener) {
        List<GameModeConfig> configs = new ArrayList<>(this.configs.values());
        for (GameModeConfig config : configs) {
            this.updateListeners.computeIfAbsent(config.getId(), k -> new ArrayList<>()).add(updateListener);
            this.createListeners.add(updateListener);
        }

        return configs;
    }
}
