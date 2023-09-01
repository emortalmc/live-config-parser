package dev.emortal.api.liveconfigparser.configs.gamemode;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.emortal.api.liveconfigparser.adapter.DurationAdapter;
import dev.emortal.api.liveconfigparser.configs.ConfigCollection;
import dev.emortal.api.liveconfigparser.parser.ConfigParser;
import io.kubernetes.client.openapi.ApiClient;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

public final class GameModeCollection extends ConfigCollection<GameModeConfig> {
    public static final Path FILE_SYSTEM_PATH = Path.of("./config/gamemodes");

    private static final String NAMESPACE = System.getenv("NAMESPACE");
    private static final String CONFIG_MAP_NAME = "gamemodes";

    public static @NotNull GameModeCollection fromKubernetes(@NotNull ApiClient client, @NotNull String namespace, @NotNull String configMapName) {
        return new GameModeCollection(client, namespace, configMapName);
    }

    public static @NotNull GameModeCollection fromKubernetes(@NotNull ApiClient client) {
        return fromKubernetes(client, NAMESPACE, CONFIG_MAP_NAME);
    }

    public static @NotNull GameModeCollection fromLocalPath(@NotNull Path localPath) throws IOException {
        return new GameModeCollection(localPath);
    }

    private GameModeCollection(@NotNull ApiClient client, @NotNull String namespace, @NotNull String configMapName) {
        super(new Parser(), client, namespace, configMapName);
    }

    private GameModeCollection(@NotNull Path localPath) throws IOException {
        super(new Parser(), localPath);
    }

    private static final class Parser implements ConfigParser<GameModeConfig> {
        private static final Gson GSON = new GsonBuilder()
                .registerTypeAdapter(Duration.class, new DurationAdapter().nullSafe())
                .create();

        @Override
        public @NotNull GameModeConfig parse(@NotNull String content) {
            return GSON.fromJson(content, GameModeConfig.class);
        }
    }
}
