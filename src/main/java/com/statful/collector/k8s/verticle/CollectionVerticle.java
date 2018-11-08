package com.statful.collector.k8s.verticle;

import com.statful.collector.k8s.NodeMetricsCollector;
import com.statful.collector.k8s.clients.KubeApi;
import com.statful.collector.k8s.utils.Loggable;
import com.statful.converter.prometheus.TextParser;
import io.vertx.core.Future;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.EventBus;

public class CollectionVerticle extends AbstractVerticle implements Loggable {

    private static final String COLLECT_SCHEDULER_PERIOD = "60000";

    @Override
    public void start(Future<Void> startFuture) {
        final int collectSchedulerPeriod = Integer.valueOf(System.getProperty("collector.period", COLLECT_SCHEDULER_PERIOD));
        final String ignoredMetricsPattern = System.getProperty("collector.ignore", "");

        final EventBus eventBus = vertx.eventBus();
        final KubeApi.Client client = new KubeApi.Client(eventBus);
        final TextParser textParser = new TextParser(ignoredMetricsPattern);
        final NodeMetricsCollector nodeMetricsCollector = new NodeMetricsCollector(client, eventBus, textParser);

        vertx.setPeriodic(collectSchedulerPeriod, id -> nodeMetricsCollector.collect());
    }
}
