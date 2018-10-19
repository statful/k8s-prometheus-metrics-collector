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

    private final KubeApi.Client kubeApi;
    private final EventBus eventBus;
    private final Converter converter;

    public NodeMetricsCollector(KubeApi.Client kubeApi, EventBus eventBus, Converter converter) {
        this.kubeApi = kubeApi;
        this.eventBus = eventBus;
        this.converter = converter;
    }

    public void collect() {
        final Flowable<String> names = getNodeNames().cache();

        final Flowable<CustomMetric> nodeMetrics = names
                .flatMap(node -> kubeApi.getNodeMetrics(node).toFlowable()
                        .flatMap(text -> converter.rxConvert(text, buildNodeTag(node)))
                        .onErrorResumeNext(e -> {
                            log().error("Failed to convert metrics for node {0}", node);
                        }));

        final Flowable<CustomMetric> cAdvisorNodeMetrics = names
                .flatMap(node -> kubeApi.getCAdvisorNodeMetrics(node).toFlowable()
                        .flatMap(text -> converter.rxConvert(text, buildNodeTag(node)))
                        .onErrorResumeNext(e -> {
                            log().error("Failed to convert metrics for node {0}", node);
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

    private Flowable<String> getNodeNames() {
        return kubeApi.getNodes()
                .flattenAsFlowable(response -> response.getJsonArray(ITEMS))
                .cast(JsonObject.class)
                .map(item -> item.getJsonObject(METADATA))
                .map(metadata -> metadata.getString(NAME));
    }

    private ArrayList<Pair<String, String>> buildNodeTag(String node) {
        return Lists.newArrayList(new Pair<>("node", node));
    }
}
