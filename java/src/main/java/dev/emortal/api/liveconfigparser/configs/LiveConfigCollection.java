package dev.emortal.api.liveconfigparser.configs;

import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeCollection;
import io.kubernetes.client.openapi.ApiClient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;

public final class LiveConfigCollection implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(LiveConfigCollection.class);

    private final @Nullable GameModeCollection gameModeCollection;

    public LiveConfigCollection(@Nullable ApiClient apiClient) throws IOException {
        if (apiClient != null || Files.exists(GameModeCollection.FILE_SYSTEM_PATH)) {
            this.gameModeCollection = new GameModeCollection(apiClient);
        } else {
            LOGGER.warn("GameModeCollection not found, disabling");
            this.gameModeCollection = null;
        }
    }

    public @Nullable GameModeCollection gameModes() {
        return this.gameModeCollection;
    }

    @Override
    public void close() throws IOException {
        if (this.gameModeCollection != null) this.gameModeCollection.close();
    }
}
