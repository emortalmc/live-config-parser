package dev.emortal.api.liveconfigparser.watcher;

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.informer.ListerWatcher;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.CallGeneratorParams;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watchable;
import okhttp3.Call;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class KubernetesConfigWatcher implements ConfigWatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesConfigWatcher.class);

    private final @NotNull String namespace;
    private final @NotNull String configMapName;

    private final @NotNull ConfigWatcherConsumer consumer;
    private final @NotNull SharedIndexInformer<V1ConfigMap> indexInformer;

    private final Map<String, byte[]> configHashes = Collections.synchronizedMap(new HashMap<>());
    private final CountDownLatch initialRequestLatch = new CountDownLatch(1);
    private final AtomicLong lastNotFoundError = new AtomicLong(0L);

    public KubernetesConfigWatcher(@NotNull ApiClient client, @NotNull String namespace, @NotNull String configMapName,
                                   @NotNull ConfigWatcherConsumer consumer) {
        this.namespace = namespace;
        this.configMapName = configMapName;

        this.consumer = consumer;

        CoreV1Api api = new CoreV1Api(client);
        ListerWatcher<V1ConfigMap, V1ConfigMapList> listerWatcher = new ConfigMapListerWatcher(client, api, namespace, configMapName);
        this.indexInformer = this.startInformer(client, listerWatcher);

        try {
            boolean result = this.initialRequestLatch.await(5, TimeUnit.SECONDS);
            if (!result) {
                LOGGER.error("Timed out getting initial ConfigMap (namespace: {}, name: {})", namespace, configMapName);
            } else {
                LOGGER.info("Got initial ConfigMap (namespace: {}, name: {})", namespace, configMapName);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private @NotNull SharedIndexInformer<V1ConfigMap> startInformer(@NotNull ApiClient apiClient,
                                                                    @NotNull ListerWatcher<V1ConfigMap, V1ConfigMapList> listerWatcher) {
        SharedInformerFactory factory = new SharedInformerFactory(apiClient);

        SharedIndexInformer<V1ConfigMap> configInformer = factory.sharedIndexInformerFor(listerWatcher, V1ConfigMap.class, 10 * 60 * 1000L, this::onError);
        configInformer.addEventHandler(new EventHandler());

        factory.startAllRegisteredInformers();
        return configInformer;
    }

    private void onError(@NotNull Class<V1ConfigMap> type, @NotNull Throwable error) {
        if (error instanceof ApiException apiException && apiException.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
            this.sendNotFoundError();
            return;
        }
        LOGGER.error("Failed to get config map '{}' in namespace '{}'", this.configMapName, this.namespace, error);
    }

    private void sendNotFoundError() {
        // Only send the not found error every 30 seconds to avoid console spam
        long now = System.currentTimeMillis();
        long lastSent = this.lastNotFoundError.getAndSet(now);
        if (now - lastSent < 30 * 1000L) return;

        LOGGER.warn("Could not find config map '{}' in namespace '{}'", this.configMapName, this.namespace);
    }

    @Override
    public void close() {
        this.indexInformer.stop();
    }

    private record ConfigMapListerWatcher(@NotNull ApiClient client, @NotNull CoreV1Api api, @NotNull String namespace,
                                          @NotNull String configMapName) implements ListerWatcher<V1ConfigMap, V1ConfigMapList> {

        @Override
        public @NotNull V1ConfigMapList list(@NotNull CallGeneratorParams params) throws ApiException {
            Call call = this.generateCall(params);
            return this.client.<V1ConfigMapList>execute(call, V1ConfigMapList.class).getData();
        }

        @Override
        public @NotNull Watchable<V1ConfigMap> watch(@NotNull CallGeneratorParams params) throws ApiException {
            Call call = this.generateCall(params);
            call = this.client.getHttpClient().newCall(call.request());
            return Watch.createWatch(this.client, call, TypeToken.getParameterized(Watch.Response.class, V1ConfigMap.class).getType());
        }

        private @NotNull Call generateCall(@NotNull CallGeneratorParams params) throws ApiException {
            String version = "0";
            if (params.resourceVersion != null && !params.resourceVersion.isEmpty()) {
                version = params.resourceVersion;

                if (!version.equals("0")) {
                    version = String.valueOf(Integer.parseInt(version));
                }
            }

            return this.api.listNamespacedConfigMapCall(
                    this.namespace, null, null, null,
                    "metadata.name=" + this.configMapName, null, null,
                    version, null, null, params.timeoutSeconds, params.watch, null
            );
        }
    }

    private final class EventHandler implements ResourceEventHandler<V1ConfigMap> {

        private final String namespace = KubernetesConfigWatcher.this.namespace;
        private final String configMapName = KubernetesConfigWatcher.this.configMapName;

        @Override
        public void onAdd(@NotNull V1ConfigMap config) {
            V1ObjectMeta meta = config.getMetadata();
            if (meta == null || !this.configMapName.equals(meta.getName())) return;

            if (!KubernetesConfigWatcher.this.configHashes.isEmpty()) {
                LOGGER.warn("ConfigMap created but should already exist? (namespace: {}, name: {})", this.namespace, this.configMapName);
            }

            LOGGER.info("ConfigMap updated (namespace: {}, name: {})", this.namespace, this.configMapName);
            this.processUpdate(config);
        }

        @Override
        public void onUpdate(@NotNull V1ConfigMap oldConfig, @NotNull V1ConfigMap newConfig) {
            V1ObjectMeta meta = newConfig.getMetadata();
            if (meta == null || !this.configMapName.equals(meta.getName())) return;

            LOGGER.info("ConfigMap updated (namespace: {}, name: {})", this.namespace, this.configMapName);
            this.processUpdate(newConfig);
        }

        @Override
        public void onDelete(@NotNull V1ConfigMap config, boolean deletedFinalStateUnknown) {
            LOGGER.info("ConfigMap deleted (namespace: {}, name: {})", this.namespace, this.configMapName);
        }

        private void processUpdate(@NotNull V1ConfigMap configMap) {
            Map<String, String> data = configMap.getData();
            if (data == null) {
                LOGGER.warn("ConfigMap data is null (namespace: {}, name: {})", this.namespace, this.configMapName);
                return;
            }

            // Clone the set as keySet is backed by the map
            Set<String> deletedConfigs = new HashSet<>(KubernetesConfigWatcher.this.configHashes.keySet());

            for (Map.Entry<String, String> entry : data.entrySet()) {
                String fileName = entry.getKey();
                String fileContents = entry.getValue();

                boolean isDeleted = deletedConfigs.remove(fileName);
                if (isDeleted) {
                    // Config already existed. Check has to see if modified.
                    this.updateHash(fileName, fileContents);
                } else {
                    // Config is newly created
                    this.createHash(fileName, fileContents);
                }
            }

            // Any configs left in deletedConfigs have been deleted
            for (String deletedConfig : deletedConfigs) {
                KubernetesConfigWatcher.this.configHashes.remove(deletedConfig);
                KubernetesConfigWatcher.this.consumer.onConfigDelete(deletedConfig);
            }

            KubernetesConfigWatcher.this.initialRequestLatch.countDown();
        }

        private void createHash(@NotNull String fileName, @NotNull String contents) {
            byte[] hash = DigestUtils.md5(contents);

            KubernetesConfigWatcher.this.configHashes.put(fileName, hash);
            KubernetesConfigWatcher.this.consumer.onConfigCreate(fileName, contents);
        }

        private void updateHash(@NotNull String fileName, @NotNull String contents) {
            byte[] existingHash = KubernetesConfigWatcher.this.configHashes.get(fileName);
            byte[] newHash = DigestUtils.md5(contents);
            if (MessageDigest.isEqual(existingHash, newHash)) return; // Config not modified, don't need to update anything

            KubernetesConfigWatcher.this.configHashes.put(fileName, newHash);
            KubernetesConfigWatcher.this.consumer.onConfigModify(fileName, contents);
        }
    }
}
