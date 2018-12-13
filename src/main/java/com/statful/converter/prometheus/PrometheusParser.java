package com.statful.converter.prometheus;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.statful.client.CustomMetric;
import com.statful.client.MetricType;
import com.statful.collector.k8s.utils.Loggable;
import com.statful.converter.Converter;
import com.statful.utils.Pair;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public class PrometheusParser extends Converter implements Loggable {

    private static final Pattern TYPE_PATTERN = Pattern.compile("# TYPE (?<name>[a-z_]+) (?<type>[a-z]+)");
    private static final Pattern SAMPLE_PATTERN = Pattern.compile("^(?<name>[a-z_]+)(\\{(?<tags>[a-zA-Z0-9.+:/%?=&<>_\",-]+)})? (?<value>[0-9A-Za-z.e+-]+)");

    private static final String NAME = "name";
    private static final String TYPE = "type";
    private static final String HISTOGRAM = "histogram";
    private static final String SUMMARY = "summary";
    private static final String VALUE = "value";
    private static final String NOT_A_NUMBER = "NaN";
    private static final String TAGS = "tags";
    private static final String COUNTER = "counter";
    private static final String GAUGE = "gauge";

    private static final Map<String, MetricType> METRIC_TYPE_CONVERTER = new ImmutableMap.Builder<String, MetricType>()
            .put(HISTOGRAM, MetricType.TIMER)
            .put(SUMMARY, MetricType.TIMER)
            .put(COUNTER, MetricType.COUNTER)
            .put(GAUGE, MetricType.GAUGE)
            .build();

    private Pattern ignorePattern;
    private boolean shouldFilter;

    public PrometheusParser(String ignorePattern) {
        if (ignorePattern != null && !ignorePattern.isEmpty()) {
            try {
                this.ignorePattern = Pattern.compile(ignorePattern);
                this.shouldFilter = true;
            } catch (PatternSyntaxException e) {
                this.ignorePattern = null;
                this.shouldFilter = false;
                log().error("Invalid ignore pattern regex", e);
            }
        } else {
            this.ignorePattern = null;
            this.shouldFilter = false;
        }
    }

    @Override
    protected CustomMetric.Builder beforeBuild(CustomMetric.Builder builder) {
        return builder;
    }

    @Override
    protected CustomMetric afterBuild(CustomMetric metrics) {
        return metrics;
    }

    @Override
    protected String beforeConversion(String metrics) {
        return metrics;
    }

    @Override
    protected List<CustomMetric> afterConversion(List<CustomMetric> metrics) {
        return metrics;
    }

    @Override
    public List<CustomMetric> convert(String text) {
        return convert(text, Collections.emptyList());
    }

    @Override
    public List<CustomMetric> convert(String text, List<Pair<String, String>> tags) {
        final List<CustomMetric> convertedMetrics = new ArrayList<>();
        convert(text, tags, convertedMetrics::add);
        return afterConversion(convertedMetrics);
    }

    @Override
    public Flowable<CustomMetric> rxConvert(String text) {
        return rxConvert(text, Collections.emptyList());
    }

    @Override
    public Flowable<CustomMetric> rxConvert(String text, List<Pair<String, String>> tags) {
        return Flowable.create(source -> {
            try {
                convert(text, tags, source::onNext);
            } catch (Throwable e) {
                source.onError(e);
            } finally {
                source.onComplete();
            }
        }, BackpressureStrategy.BUFFER);
    }

    @Override
    public void convert(String text, List<Pair<String, String>> tags, Consumer<CustomMetric> customMetricConsumer) {
        final String[] metricLines = splitByLines(text);

        String metricName = "";
        String metricType = "";

        for (String line : metricLines) {
            // Ignore metrics with names that match the given regex
            if (shouldFilter && !metricName.isEmpty() && ignorePattern.matcher(metricName).find()) {
                continue;
            }

            if (line.startsWith("# TYPE")) {
                final Matcher matcher = TYPE_PATTERN.matcher(line);

                while (matcher.find()) {
                    metricName = matcher.group(NAME);
                    metricType = matcher.group(TYPE);
                }
            } else if (line.charAt(0) != '#') {
                final Matcher matcher = SAMPLE_PATTERN.matcher(line);

                while (matcher.find()) {
                    final String sampleMetricName = matcher.group(NAME);
                    final String tagGroup = matcher.group(TAGS);
                    final String value = matcher.group(VALUE);

                    //TODO: statful histogram support
                    if (!value.equals(NOT_A_NUMBER) && isHistogramAggregationOrValidType(metricType, sampleMetricName)) {
                        CustomMetric customMetric = buildCustomMetric(metricName, metricType, sampleMetricName, tagGroup, value, tags);
                        customMetricConsumer.accept(afterBuild(customMetric));
                    }
                }
            }
        }
    }

    private boolean isHistogramAggregationOrValidType(String metricType, String sampleMetricName) {
        final boolean isCounterOrGauge = metricType.equals(COUNTER) || metricType.equals(GAUGE);
        final boolean isSummaryOrHistogram = metricType.equals(SUMMARY) || metricType.equals(HISTOGRAM);
        final boolean isAggregation = sampleMetricName.endsWith("_sum") || sampleMetricName.endsWith("_count");
        return isCounterOrGauge || (isSummaryOrHistogram && isAggregation);
    }

    private String[] splitByLines(String text) {
        final String metrics = beforeConversion(text);
        return metrics.split("\n");
    }

    private CustomMetric buildCustomMetric(String metricName,
                                           String metricType,
                                           String sampleMetricName,
                                           String tagGroup,
                                           String value,
                                           List<Pair<String, String>> tags) {
        final List<Pair<String, String>> metricTags = getTags(tagGroup);
        metricTags.addAll(tags);

        final CustomMetric.Builder customMetricBuilder = new CustomMetric.Builder()
                .withMetricName(sampleMetricName)
                .withTags(metricTags)
                .withMetricType(getMetricType(metricType, metricName, sampleMetricName))
                .withValue(new BigDecimal(value).doubleValue());

        return beforeBuild(customMetricBuilder).build();
    }

    private MetricType getMetricType(String metricType, String metricName, String sampleMetricName) {
        if (metricType.equals(HISTOGRAM) || metricType.equals(SUMMARY)) {
            return MetricType.COUNTER;
        } else {
            if (!sampleMetricName.equals(metricName)) {
                log().warn("Metric sample of type {0} has different name {1} from sample group {2}", metricType, sampleMetricName, metricName);
            }

            return METRIC_TYPE_CONVERTER.get(metricType);
        }
    }

    private static List<Pair<String, String>> getTags(String tagGroup) {
        if (!Strings.isNullOrEmpty(tagGroup)) {
            final String[] tags = tagGroup.split(",");

            return Arrays.stream(tags)
                    .map(tag -> tag.split("="))
                    .map(tag -> new Pair<>(tag[0], tag[1].substring(1, tag[1].length() - 1)))
                    .filter(tag -> !Strings.isNullOrEmpty(tag.getRight()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
