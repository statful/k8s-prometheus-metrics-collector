package com.statful.collector.k8s;

import com.statful.client.CustomMetric;
import com.statful.client.CustomMetricsConsumer;
import com.statful.client.MetricType;
import com.statful.client.StatfulMetricsOptions;
import com.statful.collector.k8s.clients.KubeApi;
import com.statful.converter.Converter;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.eventbus.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class NodeMetricsCollectorTest {

    private static final StatfulMetricsOptions STATFUL_METRICS_OPTIONS = new StatfulMetricsOptions()
            .setNamespace("test");

    @Mock
    private KubeApi.Client kubeApi;
    @Mock
    private EventBus eventBus;
    @Mock
    private Converter converter;

    private NodeMetricsCollector victim;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        victim = new NodeMetricsCollector(kubeApi, eventBus, converter);
    }

    @Test
    void collect() {
        when(kubeApi.getNodes()).thenReturn(mockNodes());
        when(kubeApi.getNodeMetrics(anyString())).thenReturn(Single.just("metrics"));
        when(kubeApi.getCAdvisorNodeMetrics(anyString())).thenReturn(Single.just("metrics"));
        when(converter.rxConvert(eq("metrics"), anyList())).thenReturn(mockCustomMetrics());

        victim.collect();

        verify(converter, times(6)).rxConvert(eq("metrics"), anyList());
        verify(eventBus, times(18)).send(eq(CustomMetricsConsumer.ADDRESS), any(CustomMetric.class));
    }

    @Test
    void collectWithErrorConverting() {
        when(kubeApi.getNodes()).thenReturn(mockNodes());
        when(kubeApi.getNodeMetrics(anyString())).thenReturn(Single.just("metrics"));
        when(kubeApi.getCAdvisorNodeMetrics(anyString())).thenReturn(Single.just("metrics"));
        when(converter.rxConvert(eq("metrics"), anyList()))
                .thenReturn(Flowable.error(new IllegalArgumentException()))
                .thenReturn(mockCustomMetrics());

        victim.collect();

        verify(converter, times(6)).rxConvert(eq("metrics"), anyList());
        verify(eventBus, times(15)).send(eq(CustomMetricsConsumer.ADDRESS), any(CustomMetric.class));
    }

    @Test
    void collectWithErrorSending() {
        when(kubeApi.getNodes()).thenReturn(mockNodes());
        when(kubeApi.getNodeMetrics(anyString())).thenReturn(Single.just("metrics"));
        when(kubeApi.getCAdvisorNodeMetrics(anyString())).thenReturn(Single.just("metrics"));
        when(converter.rxConvert(eq("metrics"), anyList())).thenReturn(mockCustomMetrics());
        when(eventBus.send(eq(CustomMetricsConsumer.ADDRESS), any(CustomMetric.class)))
                .thenThrow(new IllegalArgumentException())
                .then(invocationOnMock -> null);

        victim.collect();

        verify(converter, times(6)).rxConvert(eq("metrics"), anyList());
        verify(eventBus, times(18)).send(eq(CustomMetricsConsumer.ADDRESS), any(CustomMetric.class));
    }

    private Single<JsonObject> mockNodes() {
        final JsonArray items = Stream.of("node1", "node2", "node3")
                .map(node -> new JsonObject().put("name", node))
                .map(metadata -> new JsonObject().put("metadata", metadata))
                .reduce(new JsonArray(), JsonArray::add, JsonArray::addAll);

        return Single.just(new JsonObject().put("items", items));
    }

    private Flowable<CustomMetric> mockCustomMetrics() {
        final CustomMetric metrics = new CustomMetric.Builder()
                .withMetricType(MetricType.COUNTER)
                .withMetricName("metrics")
                .withValue(1)
                .build();
        metrics.setOptions(STATFUL_METRICS_OPTIONS);
        return Flowable.just(metrics, metrics, metrics);
    }
}
