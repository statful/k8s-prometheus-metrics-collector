package com.statful.collector.k8s;

import com.google.common.collect.Lists;
import com.statful.client.CustomMetric;
import com.statful.client.CustomMetricsConsumer;
import com.statful.client.MetricType;
import com.statful.collector.k8s.clients.KubeApi;
import com.statful.collector.k8s.utils.Loggable;
import com.statful.converter.Converter;
import com.statful.utils.Pair;
import io.reactivex.Flowable;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;

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

    private final Boolean cAdvisorMetricsDisabled;
    private final Boolean nodeMetricsDisabled;
    private final Boolean metricsServerMetricsDisabled;

    public NodeMetricsCollector(KubeApi.Client kubeApi,
                                EventBus eventBus,
                                Converter converter) {
        this.kubeApi = kubeApi;
        this.eventBus = eventBus;
        this.converter = converter;
        this.cAdvisorMetricsDisabled = Boolean.valueOf(System.getProperty("collector.cadvisor.disabled", "false"));
        this.nodeMetricsDisabled = Boolean.valueOf(System.getProperty("collector.nodes.disabled", "false"));
        this.metricsServerMetricsDisabled = Boolean.valueOf(System.getProperty("collector.metricsserver.disabled", "false"));
    }

    public NodeMetricsCollector(KubeApi.Client kubeApi,
                                EventBus eventBus,
                                Converter converter,
                                Boolean cAdvisorMetricsDisabled,
                                Boolean nodeMetricsDisabled,
                                Boolean metricsServerMetricsDisabled) {
        this.kubeApi = kubeApi;
        this.eventBus = eventBus;
        this.converter = converter;
        this.cAdvisorMetricsDisabled = cAdvisorMetricsDisabled;
        this.nodeMetricsDisabled = nodeMetricsDisabled;
        this.metricsServerMetricsDisabled = metricsServerMetricsDisabled;
    }

    public void collect() {
        final Flowable<JsonObject> names = getNodeMetadata().cache();

        Flowable.mergeDelayError(Lists
                .newArrayList(
                        names.flatMap(this::getNodeMetrics, 1),
                        names.flatMap(this::getCAdvisorMetrics, 1),
                        names.flatMap(this::getMetricsServerNodeMetrics, 1),
                        getMetricsServerPodsMetrics()), 1)
                .subscribe(this::sendMetric, error -> log().error("Failed to send metrics", error));
    }

    private Flowable<CustomMetric> getNodeMetrics(JsonObject node) {
        return nodeMetricsDisabled ? Flowable.empty() : kubeApi.getNodeMetrics(node.getString(NAME))
                .flatMapPublisher(text -> converter.rxConvert(text, buildNodeTags(node)))
                .onErrorResumeNext(e -> {
                    log().error("Failed to convert metrics for node {0}", e, node.getString("name"));
                    return Flowable.empty();
                });
    }

    private Flowable<CustomMetric> getCAdvisorMetrics(JsonObject node) {
        return cAdvisorMetricsDisabled ? Flowable.empty() : kubeApi.getCAdvisorNodeMetrics(node.getString(NAME))
                .flatMapPublisher(text -> converter.rxConvert(text, buildNodeTags(node)))
                .onErrorResumeNext(e -> {
                    log().error("Failed to convert cAdvisor metrics for node {0}", e, node.getString("name"));
                    return Flowable.empty();
                });
    }

    private Flowable<CustomMetric> getMetricsServerNodeMetrics(JsonObject node) {
        return metricsServerMetricsDisabled ? Flowable.empty() : kubeApi.getMetricsServerNodeMetrics(node.getString(NAME))
                .flatMapPublisher(metrics -> parseNodeMetrics(metrics, buildNodeTags(node)))
                .onErrorResumeNext(e -> {
                    log().error("Failed to convert metrics-server metrics for node {0}", e, node.getString("name"));
                    return Flowable.empty();
                });
    }

    private Flowable<CustomMetric> getMetricsServerPodsMetrics() {
        return metricsServerMetricsDisabled ? Flowable.empty() : kubeApi.getMetricsServerPodsMetrics()
                .flattenAsFlowable(result -> result.getJsonArray("items"))
                .cast(JsonObject.class)
                .flatMap(this::parsePodMetrics)
                .onErrorResumeNext(e -> {
                    log().error("Failed to convert metrics-server metrics for pods", e);
                    return Flowable.empty();
                });
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

    private Flowable<CustomMetric> parseNodeMetrics(JsonObject nodeMetrics, List<Pair<String, String>> tags) {
        return buildUsageMetrics(nodeMetrics, tags);
    }

    private Flowable<CustomMetric> parsePodMetrics(JsonObject podMetrics) {
        return Flowable.fromIterable(podMetrics.getJsonArray("containers"))
                .cast(JsonObject.class)
                .flatMap(container -> buildUsageMetrics(container, buildContainerTags(container)));
    }

    private Flowable<CustomMetric> buildUsageMetrics(JsonObject json, List<Pair<String, String>> tags) {
        final JsonObject usage = json.getJsonObject("usage");

        final long cpu = Long.parseLong(usage.getString("cpu").replaceAll("\\D+", ""));
        final long memory = Long.parseLong(usage.getString("memory").replaceAll("\\D+", ""));

        final CustomMetric cpuMetric = new CustomMetric.Builder()
                .withMetricName("node.cpu")
                .withValue(cpu)
                .withTags(tags)
                .withMetricType(MetricType.COUNTER)
                .build();

        final CustomMetric memoryMetric = new CustomMetric.Builder()
                .withMetricName("node.memory")
                .withValue(memory)
                .withTags(tags)
                .withMetricType(MetricType.COUNTER)
                .build();

        return Flowable.fromArray(cpuMetric, memoryMetric);
    }

    private ArrayList<Pair<String, String>> buildContainerTags(JsonObject container) {
        return Lists.newArrayList(new Pair<>("container", container.getString("name")));
    }

    private List<Pair<String, String>> buildNodeTags(JsonObject node) {
        return Lists.newArrayList(new Pair<>("node", node.getString(NAME)),
                new Pair<>(ROLE, node.getJsonObject(LABELS).getString(KUBERNETES_IO_ROLE)));
    }
}
