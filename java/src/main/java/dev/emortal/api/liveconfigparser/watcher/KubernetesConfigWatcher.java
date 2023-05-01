package dev.emortal.api.liveconfigparser.watcher;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.kubernetes.client.ProtoClient;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiCallback;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.CallGeneratorParams;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class KubernetesConfigWatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesConfigWatcher.class);

    private final @NotNull ApiClient kubeClient;
    private final @NotNull CoreV1Api api;

    private final @NotNull String namespace;
    private final @NotNull String configMapName;

    private final @NotNull ConfigWatcherConsumer consumer;

    private final @NotNull Map<String, byte[]> configHashes = new HashMap<>();

    private final @NotNull SharedIndexInformer<V1ConfigMap> indexInformer;

    private final CountDownLatch firstReqLatch = new CountDownLatch(1);

    public KubernetesConfigWatcher(@NotNull ApiClient kubeClient, @NotNull String namespace, @NotNull String configMapName,
                                   @NotNull ConfigWatcherConsumer consumer) {
        this.kubeClient = kubeClient;
        this.api = new CoreV1Api(kubeClient);

        this.namespace = namespace;
        this.configMapName = configMapName;

        this.consumer = consumer;

        this.indexInformer = this.startInformer();

        System.out.println("Starting informer");
        try {
            boolean result = this.firstReqLatch.await(10, TimeUnit.SECONDS);
            if (!result) {
                LOGGER.error("Timed out getting initial ConfigMap (namespace: {}, name: {})", this.namespace, this.configMapName);
            } else {
                LOGGER.info("Got initial ConfigMap (namespace: {}, name: {})", this.namespace, this.configMapName);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private @NotNull SharedIndexInformer<V1ConfigMap> startInformer() {
        SharedInformerFactory factory = new SharedInformerFactory(this.kubeClient);

        SharedIndexInformer<V1ConfigMap> configInformer = factory.sharedIndexInformerFor((CallGeneratorParams params) -> {
                    int parsedVersion = Integer.parseInt(params.resourceVersion);
                    String version = String.valueOf(parsedVersion == 0 ? parsedVersion : parsedVersion - 1);
                    return this.api.listNamespacedConfigMapCall(
                            this.namespace, null, null, null,
                            "metadata.name=" + this.configMapName, null, null,
                            version, null, params.timeoutSeconds, params.watch, null
                    );
                },
                V1ConfigMap.class, V1ConfigMapList.class, 10 * 60 * 1000L);

        configInformer.addEventHandler(new ResourceEventHandler<>() {
            @Override
            public void onAdd(V1ConfigMap obj) {
                if (!obj.getMetadata().getName().equals(configMapName)) return;

                if (configHashes.size() > 0) {
                    LOGGER.warn("ConfigMap created but should already exist? (namespace: {}, name: {})", namespace, configMapName);
                }

                processUpdate(obj);
            }

            @Override
            public void onUpdate(V1ConfigMap oldObj, V1ConfigMap newObj) {
                V1ObjectMeta meta = newObj.getMetadata();
                if (meta == null || meta.getName() == null || !meta.getName().equals(configMapName)) return;

                LOGGER.info("ConfigMap updated (namespace: {}, name: {})", namespace, configMapName);
                processUpdate(newObj);

                firstReqLatch.countDown();
            }

            @Override
            public void onDelete(V1ConfigMap obj, boolean deletedFinalStateUnknown) {
                LOGGER.info("ConfigMap deleted (namespace: {}, name: {})", namespace, configMapName);
            }
        });

        factory.startAllRegisteredInformers();

        return configInformer;
    }

    private void processUpdate(@NotNull V1ConfigMap configMap) {
        synchronized (this) {
            Map<String, String> data = configMap.getData();
            if (data == null) {
                LOGGER.warn("ConfigMap data is null (namespace: {}, name: {})", this.namespace, this.configMapName);
                return;
            }

            // Clone the set as keySet is backed by the map
            Set<String> deletedConfigs = new HashSet<>(this.configHashes.keySet());

            for (Map.Entry<String, String> entry : data.entrySet()) {
                String fileName = entry.getKey();
                String fileContents = entry.getValue();

                boolean isDeleted = deletedConfigs.remove(fileName);

                if (isDeleted) { // config already existed. Check has to see if modified
                    byte[] existingHash = this.configHashes.get(fileName);
                    byte[] newHash = DigestUtils.md5(fileContents);
                    if (!MessageDigest.isEqual(existingHash, newHash)) { // config has been modified
                        this.configHashes.put(fileName, newHash);

                        this.consumer.onConfigModify(fileName, fileContents);
                    }
                } else { // config is newly created
                    byte[] newHash = DigestUtils.md5(fileContents);
                    this.configHashes.put(fileName, newHash);

                    this.consumer.onConfigCreate(fileName, fileContents);
                }
            }

            // Any configs left in deletedConfigs have been deleted
            for (String deletedConfig : deletedConfigs) {
                this.configHashes.remove(deletedConfig);

                this.consumer.onConfigDelete(deletedConfig);
            }
        }
    }

    public void close() {
        this.indexInformer.stop();
    }
}
