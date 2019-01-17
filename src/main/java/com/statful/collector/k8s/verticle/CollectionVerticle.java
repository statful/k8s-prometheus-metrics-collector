package com.statful.collector.k8s.verticle;

import com.statful.collector.k8s.NodeMetricsCollector;
import com.statful.collector.k8s.clients.KubeApi;
import com.statful.collector.k8s.config.CollectorConfig;
import com.statful.collector.k8s.utils.Loggable;
import com.statful.converter.prometheus.PrometheusParser;
import com.statful.converter.prometheus.PrometheusParserOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.EventBus;

public class CollectionVerticle extends AbstractVerticle implements Loggable {

    private static final int COLLECT_SCHEDULER_PERIOD = 60000;

    @Override
    public void start(Future<Void> startFuture) {
        CollectorConfig.loadConfigRetriever(vertx)
                .flatMap(ConfigRetriever::rxGetConfig)
                .doOnEvent((conf, e) -> deployKubeApi(conf, startFuture))
                .subscribe(conf -> {
                    final int collectSchedulerPeriod = conf.getInteger("collector.period", COLLECT_SCHEDULER_PERIOD);

                    final EventBus eventBus = vertx.eventBus();
                    final KubeApi.Client client = new KubeApi.Client(eventBus);
                    final PrometheusParser textParser = new PrometheusParser(buildPrometheusParserOptions(conf));
                    final NodeMetricsCollector nodeMetricsCollector = new NodeMetricsCollector(client, eventBus, textParser);

                    vertx.setPeriodic(collectSchedulerPeriod, id -> nodeMetricsCollector.collect());
                }, e -> {
                    log().error("Failed to start collector.", e);
                    startFuture.fail(e);
                });
    }

    private void deployKubeApi(JsonObject config, Future<Void> startFuture) {
        vertx.getDelegate().deployVerticle(new KubeApi(config), result -> {
            if (result.succeeded()) {
                log().info("Kubernetes API client successfully deployed.");
            } else {
                log().error("Kubernetes API client failed to deploy.", result.cause());
                startFuture.fail(result.cause());
            }
        });
    }

    private PrometheusParserOptions buildPrometheusParserOptions(JsonObject config) {
        return PrometheusParserOptions.Builder.fromConfig(config).build();
    }
}
