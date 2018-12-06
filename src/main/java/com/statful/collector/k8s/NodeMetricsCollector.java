package com.statful.collector.k8s;

import com.google.common.collect.Lists;
import com.statful.client.CustomMetric;
import com.statful.client.CustomMetricsConsumer;
import com.statful.collector.k8s.clients.KubeApi;
import com.statful.collector.k8s.utils.Loggable;
import com.statful.converter.Converter;
import com.statful.utils.Pair;
import io.reactivex.Flowable;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.eventbus.EventBus;

import java.util.ArrayList;

public class NodeMetricsCollector implements Loggable {
    private static final String ITEMS = "items";
    private static final String METADATA = "metadata";
    private static final String NAME = "name";
    private static final String ROLE = "role";
    private static final String LABELS = "labels";
    private static final String KUBERNETES_IO_ROLE = "kubernetes.io/role";

    private final KubeApi.Client kubeApi;
    private final EventBus eventBus;
    private final Converter converter;

    public NodeMetricsCollector(KubeApi.Client kubeApi, EventBus eventBus, Converter converter) {
        this.kubeApi = kubeApi;
        this.eventBus = eventBus;
        this.converter = converter;
    }

    public void collect() {
        final Flowable<JsonObject> names = getNodeMetadata().cache();

        final Flowable<CustomMetric> nodeMetrics = names
                .flatMap(node -> kubeApi.getNodeMetrics(node.getString(NAME)).toFlowable()
                        .flatMap(text -> converter.rxConvert(text, buildNodeTags(node)))
                        .onErrorResumeNext(e -> {
                            log().error("Failed to convert metrics for node {0}", e, node);
                        }));

        final Flowable<CustomMetric> cAdvisorNodeMetrics = names
                .flatMap(node -> kubeApi.getCAdvisorNodeMetrics(node.getString(NAME)).toFlowable()
                        .flatMap(text -> converter.rxConvert(text, buildNodeTags(node)))
                        .onErrorResumeNext(e -> {
                            log().error("Failed to convert metrics for node {0}", e, node);
                        }));

        Flowable.merge(nodeMetrics, cAdvisorNodeMetrics)
                .subscribe(this::sendMetric, error -> log().error("Failed to send metrics", error));
    }

    private void sendMetric(CustomMetric metric) {
        try {
            eventBus.send(CustomMetricsConsumer.ADDRESS, metric);
        } catch (Throwable t) {
            log().error("Failed to send metric: {0}", t, metric.toMetricLine());
        }
    }

    private Flowable<JsonObject> getNodeMetadata() {
        return kubeApi.getNodes()
                .flattenAsFlowable(response -> response.getJsonArray(ITEMS))
                .cast(JsonObject.class)
                .map(item -> item.getJsonObject(METADATA));
    }

    private ArrayList<Pair<String, String>> buildNodeTags(JsonObject node) {
        return Lists.newArrayList(new Pair<>("node", node.getString(NAME)),
                new Pair<>(ROLE, node.getJsonObject(LABELS).getString(KUBERNETES_IO_ROLE)));
    }
}
