package com.statful.collector.k8s.verticle;

import com.statful.collector.k8s.NodeMetricsCollector;
import com.statful.collector.k8s.clients.KubeApi;
import com.statful.collector.k8s.clients.SimpleWebClient;
import com.statful.collector.k8s.config.CollectorConfig;
import com.statful.collector.k8s.utils.Loggable;
import com.statful.converter.prometheus.PrometheusParser;
import com.statful.converter.prometheus.PrometheusParserOptions;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.EventBus;

import java.util.List;

public class CollectionVerticle extends AbstractVerticle implements Loggable {

    private static final int COLLECT_SCHEDULER_PERIOD = 60000;

    @Override
    public void start(Future<Void> startFuture) {
        final Single<JsonObject> config = CollectorConfig.loadConfigRetriever(vertx)
                .flatMap(ConfigRetriever::rxGetConfig)
                .cache();

        final Single<List<String>> verticles = config
                .flatMapObservable(conf -> Observable.fromArray(new KubeApi(conf), new SimpleWebClient(conf)))
                .flatMapSingle(this::deployVerticle)
                .toList();

        config
                .zipWith(verticles, (conf, $) -> conf)
                .subscribe(conf -> {
                    final int collectSchedulerPeriod = conf.getInteger("collector.period", COLLECT_SCHEDULER_PERIOD);

                    final EventBus eventBus = vertx.eventBus();
                    final KubeApi.Client kubeApi = new KubeApi.Client(eventBus);
                    final SimpleWebClient.Client simpleWebClient = new SimpleWebClient.Client(eventBus);
                    final PrometheusParser textParser = new PrometheusParser(buildPrometheusParserOptions(conf));
                    final NodeMetricsCollector nodeMetricsCollector = new NodeMetricsCollector(kubeApi, simpleWebClient, eventBus, textParser, conf);

                    vertx.setPeriodic(collectSchedulerPeriod, id -> nodeMetricsCollector.collect());
                }, e -> {
                    log().error("Failed to start collector.", e);
                    startFuture.fail(e);
                });
    }

    private Single<String> deployVerticle(AbstractVerticle verticle) {
        return vertx.rxDeployVerticle(verticle)
                .doOnSuccess(ignore -> log().info("{0} client successfully deployed.", verticle.getClass().getName()))
                .doOnError(e -> log().error("{0} failed to deploy.", e, verticle.getClass().getName()));
    }

    private PrometheusParserOptions buildPrometheusParserOptions(JsonObject config) {
        return PrometheusParserOptions.Builder.fromConfig(config).build();
    }
}
