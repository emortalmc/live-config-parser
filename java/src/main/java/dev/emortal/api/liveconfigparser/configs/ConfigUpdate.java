package dev.emortal.api.liveconfigparser.configs;

import org.jetbrains.annotations.NotNull;

public sealed interface ConfigUpdate {

    record Create<T extends Config>(@NotNull T newConfig) implements ConfigUpdate {
    }

    record Modify<T extends Config>(@NotNull T oldConfig, @NotNull T newConfig) implements ConfigUpdate {
    }

    record Delete<T extends Config>(@NotNull T oldConfig) implements ConfigUpdate {
    }
}
