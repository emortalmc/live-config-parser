package dev.emortal.api.liveconfigparser.configs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.function.Consumer;

public interface ConfigProvider<T extends Config> extends AutoCloseable {

    @Nullable T getConfig(@NotNull String id);

    void addUpdateListener(@NotNull String id, @NotNull Consumer<ConfigUpdate> listener);

    @NotNull Collection<T> allConfigs();

    void addGlobalUpdateListener(@NotNull Consumer<ConfigUpdate> listener);

    @Override
    void close() throws IOException;
}
