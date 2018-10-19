package com.statful.collector.k8s.verticle;

import com.statful.converter.prometheus.TextParser;
import com.statful.collector.k8s.clients.KubeApi;
import com.statful.collector.k8s.NodeMetricsCollector;
import com.statful.collector.k8s.utils.Loggable;
import io.vertx.core.Future;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.EventBus;

public class CollectionVerticle extends AbstractVerticle implements Loggable {

    private static final int COLLECT_SCHEDULER_PERIOD = 5000;

    @Override
    public void start(Future<Void> startFuture) {
        final EventBus eventBus = vertx.eventBus();
        final KubeApi.Client client = new KubeApi.Client(eventBus);
        final TextParser textParser = new TextParser();
        final NodeMetricsCollector nodeMetricsCollector = new NodeMetricsCollector(client, eventBus, textParser);

        vertx.setPeriodic(COLLECT_SCHEDULER_PERIOD, id -> nodeMetricsCollector.collect());
    }
}
