package com.statful.collector.k8s.config;

import io.reactivex.Single;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.Vertx;

import java.util.Arrays;

public class CollectorConfig {
    private static final String COLLECTOR_CONFIG_FILE_PATH = "collector.config.path";
    private static final String COLLECTOR_CONFIGMAP_NAMESPACE = "collector.configmap.namespace";
    private static final String COLLECTOR_CONFIGMAP_NAME = "collector.configmap.name";
    private static final String COLLECTOR_SECRET_NAMESPACE = "collector.secret.namespace";
    private static final String COLLECTOR_SECRET_NAME = "collector.secret.name";

    private static final String DEFAULT_CONFIG_FILE_PATH = "conf/config.json";
    private static final String DEFAULT_CONFIGMAP_NAMESPACE = "default";
    private static final String DEFAULT_CONFIGMAP_NAME = "k8s-metrics-collector";
    private static final String DEFAULT_SECRET_NAMESPACE = "default";
    private static final String DEFAULT_SECRET_NAME = "statful-token";

    private static final String SYS = "sys";
    private static final String FILE = "file";
    private static final String JSON = "json";
    private static final String PATH = "path";
    private static final String CONFIGMAP = "configmap";

    private static final ConfigStoreOptions SYS_CONFIG_STORE = new ConfigStoreOptions().setType(SYS);

    private static final ConfigRetrieverOptions SYS_CONFIG_RETRIEVER_OPTIONS = new ConfigRetrieverOptions()
            .addStore(SYS_CONFIG_STORE);

    public static Single<ConfigRetriever> loadConfigRetriever(Vertx vertx) {
        final ConfigRetriever sysConfigRetriever = ConfigRetriever.create(vertx, SYS_CONFIG_RETRIEVER_OPTIONS);

        final Single<JsonObject> sysConfig = sysConfigRetriever.rxGetConfig().cache();

        final Single<ConfigStoreOptions> jsonConfigStoreOptions = sysConfig
                .map(config -> config.getString(COLLECTOR_CONFIG_FILE_PATH, DEFAULT_CONFIG_FILE_PATH))
                .map(CollectorConfig::buildJsonConfigStoreOptions);

        final Single<ConfigStoreOptions> configMapStoreOptions = sysConfig
                .map(config -> buildConfigMapStoreOptions(config.getString(COLLECTOR_CONFIGMAP_NAMESPACE, DEFAULT_CONFIGMAP_NAMESPACE),
                        config.getString(COLLECTOR_CONFIGMAP_NAME, DEFAULT_CONFIGMAP_NAME)));

        final Single<ConfigStoreOptions> secretStoreOptions = sysConfig
                .map(config -> buildSecretOptions(config.getString(COLLECTOR_SECRET_NAMESPACE, DEFAULT_SECRET_NAMESPACE),
                        config.getString(COLLECTOR_SECRET_NAME, DEFAULT_SECRET_NAME)));

        return Single.zip(Single.just(vertx), Single.just(SYS_CONFIG_STORE), jsonConfigStoreOptions, configMapStoreOptions, secretStoreOptions,
                CollectorConfig::buildConfigRetriever);
    }

    private static ConfigRetriever buildConfigRetriever(Vertx vertx, ConfigStoreOptions... cso) {
        final ConfigRetrieverOptions configRetrieverOptions = new ConfigRetrieverOptions();
        Arrays.stream(cso).forEach(configRetrieverOptions::addStore);
        return ConfigRetriever.create(vertx, configRetrieverOptions);
    }

    private static ConfigStoreOptions buildJsonConfigStoreOptions(String path) {
        return new ConfigStoreOptions()
                .setType(FILE)
                .setFormat(JSON)
                .setConfig(new JsonObject()
                        .put(PATH, path))
                .setOptional(true);
    }

    private static ConfigStoreOptions buildConfigMapStoreOptions(String namespace, String name) {
        return new ConfigStoreOptions()
                .setType(CONFIGMAP)
                .setConfig(new JsonObject()
                        .put("namespace", namespace)
                        .put("name", name))
                .setOptional(true);
    }

    private static ConfigStoreOptions buildSecretOptions(String namespace, String name) {
        return new ConfigStoreOptions()
                .setType(CONFIGMAP)
                .setConfig(new JsonObject()
                        .put("namespace", namespace)
                        .put("name", name)
                        .put("secret", true))
                .setOptional(true);
    }
}
