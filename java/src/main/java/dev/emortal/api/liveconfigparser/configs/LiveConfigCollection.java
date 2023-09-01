package dev.emortal.api.liveconfigparser.configs;

import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeCollection;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeConfig;
import io.kubernetes.client.openapi.ApiClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;

public final class LiveConfigCollection implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(LiveConfigCollection.class);

    public static @NotNull LiveConfigCollection create(@Nullable ApiClient client) throws IOException {
        GameModeCollection collection;
        if (client != null) {
            collection = GameModeCollection.fromKubernetes(client);
        } else if (Files.exists(GameModeCollection.FILE_SYSTEM_PATH)) {
            LOGGER.warn("Could not load game modes from Kubernetes. Falling back to local path...");
            collection = GameModeCollection.fromLocalPath(GameModeCollection.FILE_SYSTEM_PATH);
        } else {
            LOGGER.warn("Could not load game modes from Kubernetes or local path. Disabling...");
            collection = null;
        }
        return new LiveConfigCollection(collection);
    }

    private final @Nullable ConfigProvider<GameModeConfig> gameModeCollection;

    private LiveConfigCollection(@Nullable GameModeCollection collection) {
        this.gameModeCollection = collection;
    }

    public @Nullable ConfigProvider<GameModeConfig> gameModes() {
        return this.gameModeCollection;
    }

    @Override
    public void close() throws IOException {
        if (this.gameModeCollection != null) this.gameModeCollection.close();
    }
}
