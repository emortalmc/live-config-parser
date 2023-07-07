package dev.emortal.api.liveconfigparser.configs;

import dev.emortal.api.liveconfigparser.parser.ConfigParseException;
import dev.emortal.api.liveconfigparser.parser.ConfigParser;
import dev.emortal.api.liveconfigparser.watcher.ConfigWatcherConsumer;
import dev.emortal.api.liveconfigparser.watcher.FileSystemConfigWatcher;
import dev.emortal.api.liveconfigparser.watcher.KubernetesConfigWatcher;
import io.kubernetes.client.openapi.ApiClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public abstract class ConfigCollection<T extends Config> {
    private final @NotNull ConfigParser<T> parser;

    private final Map<String, T> configs = new HashMap<>();
    private final Map<String, List<Consumer<ConfigUpdate<T>>>> updateListeners = new ConcurrentHashMap<>();
    private final List<Consumer<ConfigUpdate<T>>> globalListeners = new ArrayList<>();

    /**
     * @param parser        the parser to use for this config collection
     * @param filePath      the path to the config file
     * @param namespace     namespace name. null if not running in kubernetes, but not depended on being null if so as other checks are used.
     * @param configMapName the config map name.
     */
    public ConfigCollection(@Nullable ApiClient kubeClient, @NotNull ConfigParser<T> parser, @NotNull Path filePath,
                            @UnknownNullability String namespace, @NotNull String configMapName) throws IOException {

        this.parser = parser;

        if (kubeClient == null) {
            new FileSystemConfigWatcher(filePath, new ConfigUpdateConsumer());
        } else {
            new KubernetesConfigWatcher(kubeClient, namespace, configMapName, new ConfigUpdateConsumer());
        }
    }

    public @NotNull T getConfig(String id, Consumer<ConfigUpdate<T>> updateListener) throws IllegalArgumentException {
        final T config = this.getConfig(id);

        this.updateListeners.computeIfAbsent(id, k -> new ArrayList<>()).add(updateListener);
        return config;
    }

    /**
     * @param id the id of the config to get
     * @return the config
     */
    public @NotNull T getConfig(@NotNull String id) throws IllegalArgumentException {
        final T config = this.configs.get(id);
        if (config == null) {
            throw new IllegalArgumentException("Config %s not found".formatted(id));
        }

        return config;
    }

    /**
     * @param updateListener a listener that will be called when a config is created, deleted or modified
     * @return a copy of the original configs
     */
    public @NotNull List<T> getAllConfigs(Consumer<ConfigUpdate<T>> updateListener) {
        final List<T> configs = new ArrayList<>(this.configs.values());
        this.globalListeners.add(updateListener);

        return configs;
    }

    /**
     * @return a copy of the original configs
     */
    public @NotNull List<T> getAllConfigs() {
        return new ArrayList<>(this.configs.values());
    }

    private class ConfigUpdateConsumer implements ConfigWatcherConsumer {
        @Override
        public void onConfigCreate(@NotNull String fileName, @NotNull String fileContents) {
            try {
                final T config = parser.parse(fileContents);
                config.setFileName(fileName);

                configs.put(fileName, config);
                this.propagateUpdate(fileName, new ConfigUpdate<>(fileName, config, ConfigUpdate.Type.CREATE));
            } catch (ConfigParseException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onConfigModify(@NotNull String fileName, @NotNull String fileContents) {
            try {
                final T config = parser.parse(fileContents);

                ConfigUpdate<T> update = new ConfigUpdate<>(fileName, config, ConfigUpdate.Type.MODIFY);
                this.propagateUpdate(fileName, update);
            } catch (ConfigParseException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onConfigDelete(@NotNull String fileName) {
            final T config = configs.remove(fileName);

            ConfigUpdate<T> update = new ConfigUpdate<>(fileName, config, ConfigUpdate.Type.DELETE);
            this.propagateUpdate(fileName, update);
        }

        private void propagateUpdate(@NotNull String fileName, @NotNull ConfigUpdate<T> update) {
            for (Consumer<ConfigUpdate<T>> listener : globalListeners) {
                listener.accept(update);
            }

            final List<Consumer<ConfigUpdate<T>>> listeners = updateListeners.get(fileName);
            if (listeners != null) {
                for (Consumer<ConfigUpdate<T>> listener : listeners) {
                    listener.accept(update);
                }
            }
        }
    }
}
