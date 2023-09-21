package dev.emortal.api.liveconfigparser.configs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface ConfigUpdate<T extends Config> {

    default @Nullable T oldConfig() {
        return null;
    }

    default @Nullable T newConfig() {
        return null;
    }

    record Create<T extends Config>(@NotNull T newConfig) implements ConfigUpdate<T> {
    }

    record Modify<T extends Config>(@NotNull T oldConfig, @NotNull T newConfig) implements ConfigUpdate<T> {
    }

    record Delete<T extends Config>(@NotNull T oldConfig) implements ConfigUpdate<T> {
    }
}
