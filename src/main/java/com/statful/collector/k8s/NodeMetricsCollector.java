package com.statful.collector.k8s;

import com.google.common.collect.Lists;
import com.statful.client.CustomMetric;
import com.statful.client.CustomMetricsConsumer;
import com.statful.client.MetricType;
import com.statful.collector.k8s.clients.KubeApi;
import com.statful.collector.k8s.clients.SimpleWebClient;
import com.statful.collector.k8s.utils.Loggable;
import com.statful.converter.Converter;
import com.statful.converter.util.ResourceQuantityParser;
import com.statful.utils.Pair;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

public class NodeMetricsCollector implements Loggable {
    private static final String ITEMS = "items";
    private static final String METADATA = "metadata";
    private static final String NAME = "name";
    private static final String ROLE = "role";
    private static final String LABELS = "labels";
    private static final String KUBERNETES_IO_ROLE = "kubernetes.io/role";

    private static final Pattern POD_GENERATED = Pattern.compile("-?\\w{9,10}-\\w{5}($|_)");

    private final KubeApi.Client kubeApi;
    private final SimpleWebClient.Client simpleWebClient;
    private final EventBus eventBus;
    private final Converter converter;

    private final Boolean cAdvisorMetricsDisabled;
    private final Boolean nodeMetricsDisabled;
    private final Boolean metricsServerMetricsDisabled;

    private final JsonArray additionalEndpoints;

    private final Map<String, List<Pair<String, String>>> nodeTagsMap;

    public NodeMetricsCollector(KubeApi.Client kubeApi,
                                SimpleWebClient.Client simpleWebClient,
                                EventBus eventBus,
                                Converter converter,
                                JsonObject config) {
        this.kubeApi = kubeApi;
        this.simpleWebClient = simpleWebClient;
        this.eventBus = eventBus;
        this.converter = converter;
        this.additionalEndpoints = config.getJsonArray("collector.additionalEndpoints", new JsonArray(Collections.emptyList()));
        this.cAdvisorMetricsDisabled = config.getBoolean("collector.cadvisor.disabled", false);
        this.nodeMetricsDisabled = config.getBoolean("collector.nodes.disabled", false);
        this.metricsServerMetricsDisabled = config.getBoolean("collector.metricsserver.disabled", false);
        this.nodeTagsMap = new ConcurrentHashMap<>();
    }

    public void collect() {
        getMetricsServerPodsMetrics();
        getPodMetrics();
        getClusterNodeMetrics();
        collectAdditionalEndpoints();
    }

    private void collectAdditionalEndpoints() {
        additionalEndpoints.stream()
                .map(JsonObject.class::cast)
                .forEach(this::collectAdditionalEndpoint);
    }

    private void collectAdditionalEndpoint(JsonObject additionalEndpoint) {
        final String url = additionalEndpoint.getString("url");
        final JsonObject tags = additionalEndpoint.getJsonObject("tags", new JsonObject(Collections.emptyMap()));

        simpleWebClient.getEndpoint(url)
                .subscribe(text -> converter.convert(text, buildAdditionalEndpointTags(tags), this::sendMetric),
                        e -> log().error("Failed to convert metrics for additional endpoint {0}", url));
    }

    private void getClusterNodeMetrics() {
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
                    .subscribe(metrics -> buildUsageMetrics("node", metrics, tags), e -> log().error("Failed to convert metrics-server metrics for node {0}", e, node));
        }
    }

    private void getMetricsServerPodsMetrics() {
        if (!metricsServerMetricsDisabled) {
            kubeApi.getMetricsServerPodsMetrics()
                    .flattenAsFlowable(result -> result.getJsonArray("items"))
                    .cast(JsonObject.class)
                    .flatMapIterable(podMetrics -> podMetrics.getJsonArray("containers"))
                    .cast(JsonObject.class)
                    .subscribe(container -> buildUsageMetrics("pod", container, buildContainerTags(container)), e -> log().error("Failed to convert metrics-server metrics for pods", e));
        }
    }

    private Flowable<JsonObject> getNodeMetadata() {
        return kubeApi.getNodes()
                .flattenAsFlowable(response -> response.getJsonArray(ITEMS))
                .cast(JsonObject.class)
                .map(item -> item.getJsonObject(METADATA));
    }

    private void getPodMetrics() {
        kubeApi.getPods()
                .flattenAsFlowable(response -> response.getJsonArray(ITEMS))
                .cast(JsonObject.class)
                .groupBy(this::trimPodGeneratedName)
                .subscribe(pods -> {
                    final String podName = pods.getKey();

                    final ArrayList<Pair<String, String>> tags = Lists.newArrayList(new Pair<>("pod_name", podName));

                    final Flowable<JsonObject> cachedPods = pods.cache();

                    cachedPods.count()
                            .map(podCount -> new CustomMetric.Builder()
                                    .withMetricName("pod")
                                    .withValue(podCount)
                                    .withAggregations(emptyList())
                                    .withTags(tags)
                                    .build())
                            .subscribe(this::sendMetric, e -> log().error("Failed to convert count metrics for pods", e));

                    cachedPods
                            .first(new JsonObject())
                            .map(pod -> pod.getJsonObject("spec"))
                            .flatMapObservable(podSpec -> {
                                final JsonArray containers = podSpec.getJsonArray("containers");
                                final String nodeName = podSpec.getString("nodeName");
                                return Observable.fromIterable(containers)
                                        .cast(JsonObject.class)
                                        .flatMap(container -> getContainerResourceMetrics(tags, container, nodeName));
                            })
                            .subscribe(this::sendMetric, e -> log().error("Failed to convert resource metrics for pods", e));
                }, e -> log().error("Failed to convert metrics for pods", e));
    }

    private ObservableSource<? extends CustomMetric> getContainerResourceMetrics(ArrayList<Pair<String, String>> tags, JsonObject container, String nodeName) {
        final String containerName = container.getString("name");
        final JsonObject resources = container.getJsonObject("resources");

        final ArrayList<Pair<String, String>> containerTags = new ArrayList<>(tags);
        containerTags.add(new Pair<>("container_name", containerName));

        if (nodeTagsMap.containsKey(nodeName)) {
            containerTags.addAll(nodeTagsMap.get(nodeName));
        } else {
            containerTags.add(new Pair<>("node", nodeName));
        }

        final JsonObject limits = resources.getJsonObject("limits", new JsonObject());
        final JsonObject requests = resources.getJsonObject("requests", new JsonObject());

        return Observable.fromArray(
                new CustomMetric.Builder()
                        .withMetricName("pod.cpu.limit")
                        .withValue(ResourceQuantityParser.parseCpuResource(limits.getString("cpu", "")))
                        .withTags(containerTags).withAggregations(emptyList())
                        .build(),
                new CustomMetric.Builder()
                        .withMetricName("pod.memory.limit")
                        .withValue(ResourceQuantityParser.parseMemoryResource(limits.getString("memory", "")))
                        .withTags(containerTags).withAggregations(emptyList())
                        .build(),
                new CustomMetric.Builder()
                        .withMetricName("pod.cpu.request")
                        .withValue(ResourceQuantityParser.parseCpuResource(requests.getString("cpu", "")))
                        .withTags(containerTags).withAggregations(emptyList())
                        .build(),
                new CustomMetric.Builder()
                        .withMetricName("pod.memory.request")
                        .withValue(ResourceQuantityParser.parseMemoryResource(requests.getString("memory", "")))
                        .withTags(containerTags).withAggregations(emptyList())
                        .build());
    }

    private String trimPodGeneratedName(JsonObject pod) {
        final String string = pod.getJsonObject(METADATA).getString(NAME);
        return POD_GENERATED.matcher(string).replaceAll("");
    }

    private void buildUsageMetrics(String name, JsonObject json, List<Pair<String, String>> tags) {
        final JsonObject usage = json.getJsonObject("usage");

        final long cpu = Long.parseLong(usage.getString("cpu").replaceAll("\\D+", ""));
        final long memory = Long.parseLong(usage.getString("memory").replaceAll("\\D+", ""));

        sendMetric(new CustomMetric.Builder()
                .withMetricName(name + ".cpu")
                .withValue(cpu).withTags(tags)
                .withMetricType(MetricType.COUNTER)
                .build());

        sendMetric(new CustomMetric.Builder()
                .withMetricName(name + ".memory")
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
        return Lists.newArrayList(new Pair<>("pod_name", container.getString("name")));
    }

    private List<Pair<String, String>> buildNodeTags(JsonObject node) {
        final String name = node.getString(NAME);
        final String role = node.getJsonObject(LABELS).getString(KUBERNETES_IO_ROLE);
        final List<Pair<String, String>> nodeTags = Lists.newArrayList(new Pair<>("node", name), new Pair<>(ROLE, role));

        nodeTagsMap.put(name, nodeTags);

        return nodeTags;
    }

    private List<Pair<String, String>> buildAdditionalEndpointTags(JsonObject name) {
        return name.stream()
                .map(entry -> new Pair<>(entry.getKey(), (String) entry.getValue()))
                .collect(Collectors.toList());
    }
}
