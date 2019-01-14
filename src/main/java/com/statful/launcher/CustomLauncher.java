package com.statful.launcher;

import com.statful.client.Aggregation;
import com.statful.client.StatfulMetricsFactoryImpl;
import com.statful.client.StatfulMetricsOptions;
import com.statful.collector.k8s.utils.Loggable;
import com.statful.utils.Pair;
import io.vertx.core.Launcher;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Objects.nonNull;

public class CustomLauncher extends Launcher implements Loggable {

    private String statfulToken;
    private String statfulHost;
    private boolean statfulDryRun;
    private final List<Pair<String, String>> tags = new ArrayList<>(1);
    private final List<Aggregation> timerAgg = new ArrayList<>();
    private final List<Aggregation> counterAgg = new ArrayList<>();
    private final List<Aggregation> gaugeAgg = new ArrayList<>();

    public static void main(String[] args) {
        new CustomLauncher().dispatch(args);
    }

    @Override
    public void beforeStartingVertx(VertxOptions options) {
        parseConfigSystemProperties();

        final StatfulMetricsOptions metricsOptions = new StatfulMetricsOptions()
                .setEnabled(true)
                .setDryrun(statfulDryRun)
                .setHost(statfulHost)
                .setToken(statfulToken)
                .setNamespace("kubernetes")
                .setTags(tags)
                .setFlushSize(1000)
                .setFlushInterval(10000)
                .setMaxBufferSize(15000);

        if (!timerAgg.isEmpty()) {
            metricsOptions.setTimerAggregations(timerAgg);
        }

        if (!counterAgg.isEmpty()) {
            metricsOptions.setCounterAggregations(counterAgg);
        }

        if (!gaugeAgg.isEmpty()) {
            metricsOptions.setGaugeAggregations(gaugeAgg);
        }

        metricsOptions.setFactory(new StatfulMetricsFactoryImpl());

        options.setMetricsOptions(metricsOptions);
    }

    private void parseConfigSystemProperties() {
        statfulToken = System.getProperty("statful.token");
        statfulHost = System.getProperty("statful.host", "api.statful.com");
        statfulDryRun = Boolean.valueOf(System.getProperty("statful.dryrun", String.valueOf(false)));
        collectAggregationProperty("statful.timer.agg", timerAgg);
        collectAggregationProperty("statful.counter.agg", counterAgg);
        collectAggregationProperty("statful.gauge.agg", gaugeAgg);
        String statfulEnvironment = System.getProperty("statful.environment");

        if (nonNull(statfulEnvironment)) {
            tags.add(new Pair<>("environment", statfulEnvironment));
        }
    }

    private void collectAggregationProperty(String propertyName, List<Aggregation> list) {
        final String property = System.getProperty(propertyName, "");
        if (!property.isEmpty()) {
            Arrays.stream(property.split(","))
                    .map(Aggregation::valueOf)
                    .forEach(list::add);
        }
    }

    @Override
    public void afterStartingVertx(Vertx vertx) {
        registerExceptionHandler(vertx);
    }

    private void registerExceptionHandler(Vertx vertx) {
        vertx.exceptionHandler(e -> log().error("Uncaught exception", e));
    }
}
