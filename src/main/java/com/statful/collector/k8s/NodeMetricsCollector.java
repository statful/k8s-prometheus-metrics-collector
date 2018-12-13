package com.statful.collector.k8s;

import com.google.common.collect.Lists;
import com.statful.client.CustomMetric;
import com.statful.client.CustomMetricsConsumer;
import com.statful.client.MetricType;
import com.statful.collector.k8s.clients.KubeApi;
import com.statful.collector.k8s.utils.Loggable;
import com.statful.converter.Converter;
import com.statful.utils.Pair;
import io.reactivex.Completable;
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
        getMetricsServerPodsMetrics();

        getNodeMetadata()
                .flatMapCompletable(this::getNodeMetrics, true, 1)
                .subscribe(() -> {
                }, error -> log().error("Failed to send metrics", error));
    }

    private Completable getNodeMetrics(JsonObject nodeInfo) {
        final String node = nodeInfo.getString(NAME);
        final List<Pair<String, String>> tags = buildNodeTags(nodeInfo);

        getNodeMetrics(node, tags);
        getMetricsServerNodeMetrics(node, tags);
        getCAdvisorMetrics(node, tags);

        return Completable.complete();
    }

    private void getNodeMetrics(String node, List<Pair<String, String>> tags) {
        if (!nodeMetricsDisabled) {
            kubeApi.getNodeMetrics(node)
                    .subscribe(text -> converter.convert(text, tags, this::sendMetric), e -> log().error("Failed to convert metrics for node {0}", e, node));
        }
    }

    private void getCAdvisorMetrics(String node, List<Pair<String, String>> tags) {
        if (!cAdvisorMetricsDisabled) {
            kubeApi.getCAdvisorNodeMetrics(node)
                    .subscribe(text -> converter.convert(text, tags, this::sendMetric), e -> log().error("Failed to convert cAdvisor metrics for node {0}", e, node));
        }
    }

    private void getMetricsServerNodeMetrics(String node, List<Pair<String, String>> tags) {
        if (!metricsServerMetricsDisabled) {
            kubeApi.getMetricsServerNodeMetrics(node)
                    .subscribe(metrics -> buildUsageMetrics(metrics, tags), e -> log().error("Failed to convert metrics-server metrics for node {0}", e, node));
        }
    }

    private void getMetricsServerPodsMetrics() {
        if (!metricsServerMetricsDisabled) {
            kubeApi.getMetricsServerPodsMetrics()
                    .flattenAsFlowable(result -> result.getJsonArray("items"))
                    .cast(JsonObject.class)
                    .flatMapIterable(podMetrics -> podMetrics.getJsonArray("containers"))
                    .cast(JsonObject.class)
                    .subscribe(container -> buildUsageMetrics(container, buildContainerTags(container)), e -> log().error("Failed to convert metrics-server metrics for pods", e));
        }
    }

    private Flowable<JsonObject> getNodeMetadata() {
        return kubeApi.getNodes()
                .flattenAsFlowable(response -> response.getJsonArray(ITEMS))
                .cast(JsonObject.class)
                .map(item -> item.getJsonObject(METADATA));
    }

    private void buildUsageMetrics(JsonObject json, List<Pair<String, String>> tags) {
        final JsonObject usage = json.getJsonObject("usage");

        final long cpu = Long.parseLong(usage.getString("cpu").replaceAll("\\D+", ""));
        final long memory = Long.parseLong(usage.getString("memory").replaceAll("\\D+", ""));

        sendMetric(new CustomMetric.Builder()
                .withMetricName("node.cpu")
                .withValue(cpu).withTags(tags)
                .withMetricType(MetricType.COUNTER)
                .build());

        sendMetric(new CustomMetric.Builder()
                .withMetricName("node.memory")
                .withValue(memory).withTags(tags)
                .withMetricType(MetricType.COUNTER)
                .build());
    }

    private void sendMetric(CustomMetric metric) {
        try {
            eventBus.send(CustomMetricsConsumer.ADDRESS, metric);
        } catch (Throwable t) {
            log().error("Failed to send metric: {0}", t, metric.toMetricLine());
        }
    }

    private ArrayList<Pair<String, String>> buildContainerTags(JsonObject container) {
        return Lists.newArrayList(new Pair<>("container", container.getString("name")));
    }

    private List<Pair<String, String>> buildNodeTags(JsonObject node) {
        return Lists.newArrayList(new Pair<>("node", node.getString(NAME)),
                new Pair<>(ROLE, node.getJsonObject(LABELS).getString(KUBERNETES_IO_ROLE)));
    }
}
