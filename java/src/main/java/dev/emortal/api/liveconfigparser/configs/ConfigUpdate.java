package dev.emortal.api.liveconfigparser.configs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConfigUpdate<T> {
    private final @NotNull String configId;
    private final @Nullable T config;

    private final @NotNull Type type;

    public ConfigUpdate(@NotNull String configId, @Nullable T config, @NotNull Type type) {
        this.configId = configId;
        this.config = config;
        this.type = type;
    }

    public enum Type {
        CREATE,
        MODIFY,
        DELETE
    }

    public @NotNull String getConfigId() {
        return configId;
    }

    public @Nullable T getConfig() {
        return config;
    }

    public @NotNull Type getType() {
        return type;
    }
}
