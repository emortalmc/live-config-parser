package dev.emortal.api.liveconfigparser.configs;

import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeCollection;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class LiveConfigCollection {
    private static final Logger LOGGER = LoggerFactory.getLogger(LiveConfigCollection.class);

    private final @NotNull Optional<GameModeCollection> gameModeCollection;

    public LiveConfigCollection(@Nullable ApiClient apiClient) throws IOException, ApiException {
        if (apiClient != null || Files.exists(GameModeCollection.FILE_SYSTEM_PATH)) {
            this.gameModeCollection = Optional.of(new GameModeCollection(apiClient));
        } else {
            LOGGER.warn("GameModeCollection not found, disabling");
            this.gameModeCollection = Optional.empty();
        }
    }

    public @NotNull Optional<GameModeCollection> gameModes() {
        return this.gameModeCollection;
    }
}
