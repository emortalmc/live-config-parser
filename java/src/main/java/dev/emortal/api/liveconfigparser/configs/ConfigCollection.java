package dev.emortal.api.liveconfigparser.configs;

import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeCollection;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

public class ConfigCollection {
    private final @NotNull Optional<GameModeCollection> gameModeCollection;

    public ConfigCollection() throws IOException {
        if (Files.exists(GameModeCollection.DEFAULT_PATH)) {
            this.gameModeCollection = Optional.of(new GameModeCollection());
        } else {
            this.gameModeCollection = Optional.empty();
        }
    }

    public @NotNull Optional<GameModeCollection> getGameModeCollection() {
        return gameModeCollection;
    }
}
