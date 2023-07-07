package dev.emortal.api.liveconfigparser.configs;

import dev.emortal.api.liveconfigparser.parser.ConfigParseException;
import dev.emortal.api.liveconfigparser.parser.ConfigParser;
import dev.emortal.api.liveconfigparser.watcher.ConfigWatcher;
import dev.emortal.api.liveconfigparser.watcher.ConfigWatcherConsumer;
import dev.emortal.api.liveconfigparser.watcher.FileSystemConfigWatcher;
import dev.emortal.api.liveconfigparser.watcher.KubernetesConfigWatcher;
import io.kubernetes.client.openapi.ApiClient;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public abstract class ConfigCollection<T extends Config> implements AutoCloseable {

    private final ConfigParser<T> parser;
    private final ConfigWatcher watcher;

    private final Map<String, T> configs = new HashMap<>();
    private final Map<String, List<Consumer<ConfigUpdate<T>>>> updateListeners = new ConcurrentHashMap<>();
    private final List<Consumer<ConfigUpdate<T>>> globalListeners = new ArrayList<>();

    /**
     * @param parser        the parser to use for this config collection
     * @param filePath      the path to the config file
     * @param namespace     namespace name. Will be null if not running in kubernetes, but this behaviour should not be depended upon.
     * @param configMapName the config map name.
     */
    public ConfigCollection(@Nullable ApiClient kubeClient, @NotNull ConfigParser<T> parser, @NotNull Path filePath,
                            @Nullable String namespace, @NotNull String configMapName) throws IOException {

        this.parser = parser;

        if (kubeClient == null) {
            this.watcher = new FileSystemConfigWatcher(filePath, new ConfigUpdateConsumer());
        } else {
            //noinspection DataFlowIssue
            this.watcher = new KubernetesConfigWatcher(kubeClient, namespace, configMapName, new ConfigUpdateConsumer());
        }
    }

    public @Nullable T getConfig(@NotNull String id, @NotNull Consumer<ConfigUpdate<T>> updateListener) {
        T config = this.getConfig(id);
        this.updateListeners.computeIfAbsent(id, k -> new ArrayList<>()).add(updateListener);
        return config;
    }

    /**
     * @param id the id of the config to get
     * @return the config
     */
    public @Nullable T getConfig(@NotNull String id) {
        return this.configs.get(id);
    }

    /**
     * @param updateListener a listener that will be called when a config is created, deleted or modified
     * @return a copy of the original configs
     */
    public @NotNull Collection<T> getAllConfigs(@NotNull Consumer<ConfigUpdate<T>> updateListener) {
        this.globalListeners.add(updateListener);
        return this.getAllConfigs();
    }

    /**
     * @return a copy of the original configs
     */
    public @NotNull Collection<T> getAllConfigs() {
        return Collections.unmodifiableCollection(this.configs.values());
    }

    @Override
    public void close() throws IOException {
        this.watcher.close();
    }

    private final class ConfigUpdateConsumer implements ConfigWatcherConsumer {

        @Override
        public void onConfigCreate(@NotNull String fileName, @NotNull String fileContents) {
            try {
                T config = ConfigCollection.this.parser.parse(fileContents);
                ConfigCollection.this.configs.put(config.id(), config);
                this.propagateUpdate(new ConfigUpdate<>(config, ConfigUpdate.Type.CREATE));
            } catch (ConfigParseException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onConfigModify(@NotNull String fileName, @NotNull String fileContents) {
            try {
                T config = ConfigCollection.this.parser.parse(fileContents);
                this.propagateUpdate(new ConfigUpdate<>(config, ConfigUpdate.Type.MODIFY));
            } catch (ConfigParseException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onConfigDelete(@NotNull String fileName) {
            T config = ConfigCollection.this.configs.remove(fileName);
            this.propagateUpdate(new ConfigUpdate<>(config, ConfigUpdate.Type.DELETE));
        }

        private void propagateUpdate(@NotNull ConfigUpdate<T> update) {
            for (var listener : ConfigCollection.this.globalListeners) {
                listener.accept(update);
            }

            final List<Consumer<ConfigUpdate<T>>> listeners = ConfigCollection.this.updateListeners.get(update.config().id());
            if (listeners != null) {
                for (var listener : listeners) {
                    listener.accept(update);
                }
            }
        }
    }
}
