package com.statful.collector.k8s;

import com.statful.client.CustomMetric;
import com.statful.client.CustomMetricsConsumer;
import com.statful.collector.k8s.clients.KubeApi;
import com.statful.collector.k8s.clients.SimpleWebClient;
import com.statful.converter.Converter;
import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.eventbus.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class NodeMetricsCollectorTest {

    @Mock
    private KubeApi.Client kubeApi;
    @Mock
    private SimpleWebClient.Client simpleWebClient;
    @Mock
    private EventBus eventBus;
    @Mock
    private Converter converter;

    private NodeMetricsCollector victim;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        victim = new NodeMetricsCollector(kubeApi, simpleWebClient, eventBus, converter, new JsonObject());
    }

    @Test
    void collect() {
        ArgumentCaptor<CustomMetric> captor = ArgumentCaptor.forClass(CustomMetric.class);

        when(kubeApi.getPods()).thenReturn(mockPods());
        when(kubeApi.getNodes()).thenReturn(mockNodes());
        when(kubeApi.getNodeMetrics(anyString())).thenReturn(Single.just("metrics"));
        when(kubeApi.getCAdvisorNodeMetrics(anyString())).thenReturn(Single.just("metrics"));
        when(kubeApi.getMetricsServerNodeMetrics(anyString())).thenReturn(mockNodeMetrics());
        when(kubeApi.getMetricsServerPodsMetrics()).thenReturn(mockPodsMetrics());

        victim.collect();

        verify(kubeApi, times(3)).getMetricsServerNodeMetrics(anyString());
        verify(converter, times(6)).convert(eq("metrics"), anyList(), any());
        verify(eventBus, times(18)).send(eq(CustomMetricsConsumer.ADDRESS), captor.capture());
    }

    @Test
    void collectWithErrorSending() {
        when(kubeApi.getPods()).thenReturn(mockPods());
        when(kubeApi.getNodes()).thenReturn(mockNodes());
        when(kubeApi.getNodeMetrics(anyString())).thenReturn(Single.just("metrics"));
        when(kubeApi.getCAdvisorNodeMetrics(anyString())).thenReturn(Single.just("metrics"));
        when(eventBus.send(eq(CustomMetricsConsumer.ADDRESS), any(CustomMetric.class)))
                .thenThrow(new IllegalArgumentException())
                .then(invocationOnMock -> null);
        when(kubeApi.getMetricsServerNodeMetrics(anyString())).thenReturn(mockNodeMetrics());
        when(kubeApi.getMetricsServerPodsMetrics()).thenReturn(mockPodsMetrics());

        victim.collect();

        verify(kubeApi, times(3)).getMetricsServerNodeMetrics(anyString());
        verify(converter, times(6)).convert(eq("metrics"), anyList(), any());
        verify(eventBus, times(17)).send(eq(CustomMetricsConsumer.ADDRESS), any(CustomMetric.class));
    }

    private Single<JsonObject> mockPods() {
        final JsonArray items = Stream.of("pod1-1234567-1234", "pod2-1234567-1234", "pod1-1234567-1234")
                .map(node -> new JsonObject()
                        .put("name", node))
                .map(metadata -> new JsonObject()
                        .put("metadata", metadata)
                        .put("spec", new JsonObject()
                                .put("nodeName", "node1")
                                .put("containers", new JsonArray()
                                        .add(new JsonObject()
                                                .put("name", "container1")
                                                .put("resources", new JsonObject()
                                                        .put("limits", new JsonObject()
                                                                .put("cpu", "2")
                                                                .put("memory", "2Gi"))
                                                        .put("requests", new JsonObject()
                                                                .put("cpu", "0.5")
                                                                .put("memory", "1G")))))))
                .reduce(new JsonArray(), JsonArray::add, JsonArray::addAll);

        return Single.just(new JsonObject().put("items", items));
    }

    private Single<JsonObject> mockNodes() {
        final JsonArray items = Stream.of("node1", "node2", "node3")
                .map(node -> new JsonObject()
                        .put("labels", new JsonObject().put("kubernetes.io/role", "node"))
                        .put("name", node))
                .map(metadata -> new JsonObject()
                        .put("metadata", metadata))
                .reduce(new JsonArray(), JsonArray::add, JsonArray::addAll);

        return Single.just(new JsonObject().put("items", items));
    }

    private Single<JsonObject> mockNodeMetrics() {
        return Single.just(new JsonObject()
                .put("usage", new JsonObject()
                        .put("cpu", "28m")
                        .put("memory", "291283Ki")));
    }

    private Single<JsonObject> mockPodsMetrics() {
        return Single.just(new JsonObject()
                .put("items", new JsonArray()
                        .add(new JsonObject()
                                .put("containers", new JsonArray()
                                        .add(new JsonObject()
                                                .put("name", "container")
                                                .put("usage", new JsonObject()
                                                        .put("cpu", "28m")
                                                        .put("memory", "291283Ki")))))));
    }
}
