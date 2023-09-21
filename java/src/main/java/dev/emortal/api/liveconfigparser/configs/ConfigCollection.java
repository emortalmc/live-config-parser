package dev.emortal.api.liveconfigparser.configs;

import dev.emortal.api.liveconfigparser.parser.ConfigParseException;
import dev.emortal.api.liveconfigparser.parser.ConfigParser;
import dev.emortal.api.liveconfigparser.watcher.ConfigWatcher;
import dev.emortal.api.liveconfigparser.watcher.ConfigWatcherConsumer;
import dev.emortal.api.liveconfigparser.watcher.FileSystemConfigWatcher;
import dev.emortal.api.liveconfigparser.watcher.KubernetesConfigWatcher;
import io.kubernetes.client.openapi.ApiClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public abstract class ConfigCollection<T extends Config> implements ConfigProvider<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigCollection.class);

    private final @NotNull ConfigParser<T> parser;
    private final @NotNull ConfigWatcher watcher;

    private final Map<String, T> configs = new HashMap<>();
    private final Map<String, String> fileNameToId = new HashMap<>();

    private final Map<String, List<Consumer<ConfigUpdate>>> updateListeners = Collections.synchronizedMap(new HashMap<>());
    private final List<Consumer<ConfigUpdate>> globalListeners = new CopyOnWriteArrayList<>();

    protected ConfigCollection(@NotNull ConfigParser<T> parser, @NotNull ApiClient client, @NotNull String namespace,
                               @NotNull String configMapName) {
        this.parser = parser;
        this.watcher = new KubernetesConfigWatcher(client, namespace, configMapName, new ConfigUpdateConsumer());
    }

    protected ConfigCollection(@NotNull ConfigParser<T> parser, @NotNull Path localPath) throws IOException {
        this.parser = parser;
        this.watcher = new FileSystemConfigWatcher(localPath, new ConfigUpdateConsumer());
    }

    @Override
    public @Nullable T getConfig(@NotNull String id) {
        return this.configs.get(id);
    }

    @Override
    public @NotNull Collection<T> allConfigs() {
        return Collections.unmodifiableCollection(this.configs.values());
    }

    @Override
    public void addUpdateListener(@NotNull String id, @NotNull Consumer<ConfigUpdate> listener) {
        this.updateListeners.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    public void addGlobalUpdateListener(@NotNull Consumer<ConfigUpdate> listener) {
        this.globalListeners.add(listener);
    }

    @Override
    public void close() throws IOException {
        this.watcher.close();
    }

    private final class ConfigUpdateConsumer implements ConfigWatcherConsumer {

        @Override
        public void onConfigCreate(@NotNull String fileName, @NotNull String fileContents) {
            T config = this.parseConfig(fileName, fileContents);
            this.addOrReplaceConfig(fileName, config);
            this.propagateUpdate(config.id(), new ConfigUpdate.Create<>(config));
        }

        @Override
        public void onConfigModify(@NotNull String fileName, @NotNull String fileContents) {
            T newConfig = this.parseConfig(fileName, fileContents);
            T oldConfig = ConfigCollection.this.getConfig(newConfig.id());
            this.addOrReplaceConfig(fileName, newConfig);
            this.propagateUpdate(newConfig.id(), new ConfigUpdate.Modify<>(oldConfig, newConfig));
        }

        private void addOrReplaceConfig(@NotNull String fileName, @NotNull T config) {
            ConfigCollection.this.fileNameToId.put(fileName, config.id());
            ConfigCollection.this.configs.put(config.id(), config);
        }

        private @NotNull T parseConfig(@NotNull String fileName, @NotNull String fileContents) {
            try {
                return ConfigCollection.this.parser.parse(fileContents);
            } catch (ConfigParseException exception) {
                LOGGER.error("Failed to parse config '{}'", fileName, exception);
                throw new RuntimeException(exception);
            }
        }

        @Override
        public void onConfigDelete(@NotNull String fileName) {
            String id = ConfigCollection.this.fileNameToId.get(fileName);
            T oldConfig = ConfigCollection.this.configs.remove(id);
            this.propagateUpdate(id, new ConfigUpdate.Delete<>(oldConfig));
        }

        private void propagateUpdate(@NotNull String id, @NotNull ConfigUpdate update) {
            for (Consumer<ConfigUpdate> listener : ConfigCollection.this.globalListeners) {
                listener.accept(update);
            }

            List<Consumer<ConfigUpdate>> listeners = ConfigCollection.this.updateListeners.get(id);
            if (listeners != null) {
                for (Consumer<ConfigUpdate> listener : listeners) {
                    listener.accept(update);
                }
            }
        }
    }
}
