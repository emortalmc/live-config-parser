package dev.emortal.api.liveconfigparser.configs.gamemode;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.emortal.api.liveconfigparser.adapter.DurationAdapter;
import dev.emortal.api.liveconfigparser.configs.ConfigCollection;
import dev.emortal.api.liveconfigparser.parser.ConfigParser;
import io.kubernetes.client.openapi.ApiClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

public class GameModeCollection extends ConfigCollection<GameModeConfig> {
    public static final Path FILE_SYSTEM_PATH = Path.of("./config/gamemodes");

    private static final @Nullable String NAMESPACE = System.getenv("NAMESPACE");
    private static final @Nullable String CONFIG_MAP_NAME = "gamemodes";

    public GameModeCollection(@Nullable ApiClient kubeClient, @Nullable String namespace, @Nullable String configMapName)
            throws IOException {

        super(kubeClient, new Parser(), FILE_SYSTEM_PATH,
                namespace == null ? NAMESPACE : namespace,
                configMapName == null ? CONFIG_MAP_NAME : configMapName);
    }

    public GameModeCollection(@Nullable ApiClient kubeClient) throws IOException {
        super(kubeClient, new Parser(), FILE_SYSTEM_PATH, NAMESPACE, CONFIG_MAP_NAME);
    }

    public GameModeCollection(Path path) throws IOException {
        super(null, new Parser(), path, NAMESPACE, CONFIG_MAP_NAME);
    }

    private static class Parser implements ConfigParser<GameModeConfig> {
        private static final Gson GSON = new GsonBuilder()
                .registerTypeAdapter(Duration.class, new DurationAdapter())
                .create();

        @Override
        public @NotNull GameModeConfig parse(@NotNull String content) {
            return GSON.fromJson(content, GameModeConfig.class);
        }
    }
}
