package com.statful.converter.prometheus;

import com.google.common.collect.Sets;
import com.statful.client.CustomMetric;
import com.statful.client.StatfulMetricsOptions;
import com.statful.utils.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PrometheusParserTest {
    private static final String IGNORED_COUNT_METRIC =
            "# HELP ignore_metric_name some comment about the metric\n" +
                    "# TYPE ignore_metric_name counter\n" +
                    "ignore_metric_name 1\n" +
                    "ignore_metric_name{key=\"value\"} 2\n" +
                    "ignore_metric_name{key=\"value\",key2=\"value2\"} 3\n" +
                    "ignore_metric_name{key=\"value\",key2=\"value2\"} 3e-2\n" +
                    "ignore_metric_name{key=\"value\",key2=\"value2\"} NaN";
    private static final String COUNT_METRIC =
            "# HELP metric_name some comment about the metric\n" +
                    "# TYPE metric_name counter\n" +
                    "metric_name 1\n" +
                    "metric_name{key=\"8c73405e-ce85-4aa0-a080-e2a646e5a4fa\"} 2\n" +
                    "metric_name{key=\"8c73405e-ce85-4aa0-a080-e2a646e5a4fa\",key2=\"k8s-prometheus-metrics-collector-7998cf8c7d-c6d87\"} 3\n" +
                    "metric_name{key=\"8c73405e-ce85-4aa0-a080-e2a646e5a4fa\",key2=\"k8s-prometheus-metrics-collector-7998cf8c7d-c6d87\"} 3e-2\n" +
                    "metric_name{key=\"8c73405e-ce85-4aa0-a080-e2a646e5a4fa\",key2=\"k8s-prometheus-metrics-collector-7998cf8c7d-c6d87\"} NaN";
    private static final String GAUGE_METRIC =
            "# HELP metric_name some comment about the metric\n" +
                    "# TYPE metric_name gauge\n" +
                    "metric_name 1\n" +
                    "metric_name{key=\"value\"} 2\n" +
                    "metric_name{key=\"value\",key2=\"value2\"} 3\n" +
                    "metric_name{key=\"value\",key2=\"value2\"} 3e+2\n" +
                    "metric_name{key=\"value\",key2=\"value2\"} NaN";
    private static final String SUMMARY_METRIC =
            "# HELP metric_name some comment about the metric\n" +
                    "# TYPE metric_name summary\n" +
                    "metric_name_bucket 1\n" +
                    "metric_name_sum{key=\"value\"} 2\n" +
                    "metric_name_count{key=\"value\",key2=\"value2\"} 3\n" +
                    "metric_name_bucket{key=\"value\",key2=\"value2\"} 3e+2";
    private static final String HISTOGRAM_METRIC =
            "# HELP metric_name some comment about the metric\n" +
                    "# TYPE metric_name histogram\n" +
                    "metric_name_bucket 1\n" +
                    "metric_name_sum{key=\"value\"} 2\n" +
                    "metric_name_count{key=\"value\",key2=\"value2\"} 3\n" +
                    "metric_name_bucket{key=\"value\",key2=\"value2\"} 3e+2";
    private static final String ALL_METRICS = COUNT_METRIC + '\n' + GAUGE_METRIC + '\n' + SUMMARY_METRIC + '\n' + HISTOGRAM_METRIC;

    private static final String EXPECTED_COUNT = "test.counter.metric_name 1 \\d.* count,sum,10 100\n" +
            "test.counter.metric_name,key=8c73405e-ce85-4aa0-a080-e2a646e5a4fa 2 \\d.* count,sum,10 100\n" +
            "test.counter.metric_name,key2=k8s-prometheus-metrics-collector-7998cf8c7d-c6d87,key=8c73405e-ce85-4aa0-a080-e2a646e5a4fa 3 \\d.* count,sum,10 100\n" +
            "test.counter.metric_name,key2=k8s-prometheus-metrics-collector-7998cf8c7d-c6d87,key=8c73405e-ce85-4aa0-a080-e2a646e5a4fa 0.03 \\d.* count,sum,10 100";
    private static final String EXPECTED_COUNT_WITH_REPLACEMENT = "test.counter.metric_name 1 \\d.* count,sum,10 100\n" +
            "test.counter.metric_name,key=_uuid_ 2 \\d.* count,sum,10 100\n" +
            "test.counter.metric_name,key2=k8s-prometheus-metrics-collector,key=_uuid_ 3 \\d.* count,sum,10 100\n" +
            "test.counter.metric_name,key2=k8s-prometheus-metrics-collector,key=_uuid_ 0.03 \\d.* count,sum,10 100";
    private static final String EXPECTED_GAUGE = "test.gauge.metric_name 1 \\d.* 100\n" +
            "test.gauge.metric_name,key=value 2 \\d.* 100\n" +
            "test.gauge.metric_name,key2=value2,key=value 3 \\d.* 100\n" +
            "test.gauge.metric_name,key2=value2,key=value 300 \\d.* 100";
    private static final String EXPECTED_SUMMARY = "test.counter.metric_name_sum,key=value 2 \\d.* count,sum,10 100\n" +
            "test.counter.metric_name_count,key2=value2,key=value 3 \\d.* count,sum,10 100";
    private static final String EXPECTED_HISTOGRAM = "test.counter.metric_name_sum,key=value 2 \\d.* count,sum,10 100\n" +
            "test.counter.metric_name_count,key2=value2,key=value 3 \\d.* count,sum,10 100";
    private static final String ALL_EXPECTATIONS = EXPECTED_COUNT + '\n' + EXPECTED_GAUGE + '\n' + EXPECTED_SUMMARY + '\n' + EXPECTED_HISTOGRAM;
    private static final String EXPECTED_TAG_NAME_PATTERN_FILTER = "test.counter.ignore_metric_name 1 \\d.* count,sum,10 100\n" +
            "test.counter.ignore_metric_name 2 \\d.* count,sum,10 100\n" +
            "test.counter.ignore_metric_name 3 \\d.* count,sum,10 100\n" +
            "test.counter.ignore_metric_name 0.03 \\d.* count,sum,10 100";
    private static final String EXPECTED_TAG_NAME_FILTER = "test.counter.ignore_metric_name 1 \\d.* count,sum,10 100\n" +
            "test.counter.ignore_metric_name 2 \\d.* count,sum,10 100\n" +
            "test.counter.ignore_metric_name,key2=value2 3 \\d.* count,sum,10 100\n" +
            "test.counter.ignore_metric_name,key2=value2 0.03 \\d.* count,sum,10 100";

    private static final StatfulMetricsOptions STATFUL_METRICS_OPTIONS = new StatfulMetricsOptions()
            .setNamespace("test");

    private PrometheusParser victim;

    @BeforeEach
    void setUp() {
        victim = new PrometheusParser(new PrometheusParserOptions.Builder().build());
    }

    @ParameterizedTest
    @MethodSource("parameterProvider")
    void convert(String metrics, String expected) {
        final List<CustomMetric> result = victim.convert(metrics);
        final String actual = printMetrics(result);
        final Matcher matcher = Pattern.compile(expected).matcher(actual);
        assertTrue(matcher.matches(), "\nexpected: " + expected + "\nactual: " + actual + "\n");
    }

    @ParameterizedTest
    @MethodSource("parameterProvider")
    void rxConvert(String metrics, String expected) {
        victim.rxConvert(metrics)
                .doOnNext(metric -> metric.setOptions(STATFUL_METRICS_OPTIONS))
                .map(CustomMetric::toMetricLine)
                .reduce((acc, ele) -> acc + '\n' + ele)
                .test()
                .assertValue(actual -> Pattern.compile(expected).matcher(actual).matches())
                .assertComplete();
    }

    @Test
    void convertWithMetricNamePatternFilter() {
        PrometheusParser victim = new PrometheusParser(new PrometheusParserOptions.Builder()
                .withIgnoreMetricNamesPattern("ignore")
                .build());

        final List<CustomMetric> result = victim.convert(IGNORED_COUNT_METRIC);
        final String actual = printMetrics(result);
        assertEquals("", actual);
    }

    @Test
    void convertWithMetricNameFilter() {
        PrometheusParser victim = new PrometheusParser(new PrometheusParserOptions.Builder()
                .withIgnoreMetricNames(Sets.newHashSet("ignore_metric_name"))
                .build());

        final List<CustomMetric> result = victim.convert(IGNORED_COUNT_METRIC);
        final String actual = printMetrics(result);
        assertEquals("", actual);
    }

    @Test
    void convertWithTagNamePatternFilter() {
        PrometheusParser victim = new PrometheusParser(new PrometheusParserOptions.Builder()
                .withIgnoreTagNamesPattern("key")
                .build());

        final List<CustomMetric> result = victim.convert(IGNORED_COUNT_METRIC);
        final String actual = printMetrics(result);
        final Matcher matcher = Pattern.compile(EXPECTED_TAG_NAME_PATTERN_FILTER).matcher(actual);
        assertTrue(matcher.matches(), "\nexpected: " + EXPECTED_TAG_NAME_PATTERN_FILTER + "\nactual: " + actual + "\n");
    }

    @Test
    void convertWithTagNameFilter() {
        PrometheusParser victim = new PrometheusParser(new PrometheusParserOptions.Builder()
                .withIgnoreTagNames(Sets.newHashSet("key"))
                .build());

        final List<CustomMetric> result = victim.convert(IGNORED_COUNT_METRIC);
        final String actual = printMetrics(result);
        final Matcher matcher = Pattern.compile(EXPECTED_TAG_NAME_FILTER).matcher(actual);
        assertTrue(matcher.matches(), "\nexpected: " + EXPECTED_TAG_NAME_FILTER + "\nactual: " + actual + "\n");
    }

    @Test
    void convertWithReplacement() {
        PrometheusParser victim = new PrometheusParser(new PrometheusParserOptions.Builder()
                .withTagValueReplacements(Arrays.asList(new Pair<>("\\w{8}-\\w{4}-\\w{4}-\\w{4}-\\w{12}", "_uuid_"),
                        new Pair<>("-?\\w{9,10}-\\w{5}($|_)", "")))
                .build());

        final List<CustomMetric> result = victim.convert(COUNT_METRIC);
        final String actual = printMetrics(result);
        final Matcher matcher = Pattern.compile(EXPECTED_COUNT_WITH_REPLACEMENT).matcher(actual);
        assertTrue(matcher.matches(), "\nexpected: " + EXPECTED_COUNT_WITH_REPLACEMENT + "\nactual: " + actual + "\n");
    }

    private static Stream<Arguments> parameterProvider() {
        return Stream.of(
                Arguments.arguments(COUNT_METRIC, EXPECTED_COUNT),
                Arguments.arguments(GAUGE_METRIC, EXPECTED_GAUGE),
                Arguments.arguments(SUMMARY_METRIC, EXPECTED_SUMMARY),
                Arguments.arguments(HISTOGRAM_METRIC, EXPECTED_HISTOGRAM),
                Arguments.arguments(ALL_METRICS, ALL_EXPECTATIONS)
        );
    }

    private String printMetrics(List<CustomMetric> metrics) {
        return metrics.stream()
                .peek(metric -> metric.setOptions(STATFUL_METRICS_OPTIONS))
                .map(CustomMetric::toMetricLine)
                .collect(Collectors.joining("\n"));
    }
}
