package com.statful.converter.prometheus;

import com.statful.utils.Pair;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class PrometheusParserOptions {
    private static final String COLLECTOR_IGNORE_METRIC_REGEX = "collector.ignore.metric.regex";
    private static final String COLLECTOR_IGNORE_METRIC = "collector.ignore.metric";
    private static final String COLLECTOR_IGNORE_TAGS_REGEX = "collector.ignore.tags.regex";
    private static final String COLLECTOR_IGNORE_TAGS = "collector.ignore.tags";
    private static final String COLLECTOR_REPLACEMENT_TAG = "collector.replacement.tag";
    private static final String PATTERN = "pattern";
    private static final String REPLACEMENT = "replacement";

    private final List<Pair<Pattern, String>> tagValueReplacements;
    private final boolean shouldFilterMetricNamesByPattern;
    private final Pattern ignoreMetricNamesPattern;
    private final Set<String> ignoreMetricNames;
    private final boolean shouldFilterTagNamesByPattern;
    private final Pattern ignoreTagNamesPattern;
    private final Set<String> ignoreTagNames;

    public PrometheusParserOptions(List<Pair<String, String>> tagValueReplacements,
                                   String ignoreMetricNamesPattern,
                                   Set<String> ignoreMetricNames,
                                   String ignoreTagNamesPattern,
                                   Set<String> ignoreTagNames) {
        this.tagValueReplacements = tagValueReplacements == null ? emptyList() : tagValueReplacements.stream()
                .map(entry -> new Pair<>(Pattern.compile(entry.getLeft()), entry.getRight()))
                .collect(toList());
        this.shouldFilterMetricNamesByPattern = ignoreMetricNamesPattern != null && !ignoreMetricNamesPattern.isEmpty();
        this.ignoreMetricNamesPattern = shouldFilterMetricNamesByPattern ? Pattern.compile(ignoreMetricNamesPattern) : null;
        this.ignoreMetricNames = ignoreMetricNames == null ? emptySet() : ignoreMetricNames;
        this.shouldFilterTagNamesByPattern = ignoreTagNamesPattern != null && !ignoreTagNamesPattern.isEmpty();
        this.ignoreTagNamesPattern = shouldFilterTagNamesByPattern ? Pattern.compile(ignoreTagNamesPattern) : null;
        this.ignoreTagNames = ignoreTagNames == null ? emptySet() : ignoreTagNames;
    }

    public List<Pair<Pattern, String>> getTagValueReplacements() {
        return tagValueReplacements == null ? emptyList() : tagValueReplacements;
    }

    public Pattern getIgnoreMetricNamesPattern() {
        return ignoreMetricNamesPattern;
    }

    public boolean shouldFilterMetricNamesByPattern() {
        return shouldFilterMetricNamesByPattern;
    }

    public Set<String> getIgnoreMetricNames() {
        return ignoreMetricNames;
    }

    public Pattern getIgnoreTagNamesPattern() {
        return ignoreTagNamesPattern;
    }

    public boolean shouldFilterTagNamesByPattern() {
        return shouldFilterTagNamesByPattern;
    }

    public Set<String> getIgnoreTagNames() {
        return ignoreTagNames;
    }

    public static final class Builder {
        private List<Pair<String, String>> tagValueReplacements;
        private String ignoreMetricNamesPattern;
        private Set<String> ignoreMetricNames;
        private String ignoreTagNamesPattern;
        private Set<String> ignoreTagNames;

        public Builder() {
        }

        public static Builder fromConfig(JsonObject config) {
            final List<Pair<String, String>> tagValueReplacements = config.getJsonArray(COLLECTOR_REPLACEMENT_TAG, new JsonArray(emptyList())).stream()
                    .map(JsonObject.class::cast)
                    .filter(replacement -> !replacement.getString(PATTERN, "").isEmpty())
                    .map(replacement -> new Pair<>(replacement.getString(PATTERN, ""), replacement.getString(REPLACEMENT, "")))
                    .collect(toList());

            final Set<String> ignoreMetricNames = config.getJsonArray(COLLECTOR_IGNORE_METRIC, new JsonArray(emptyList())).stream()
                    .map(String.class::cast)
                    .collect(toSet());

            final Set<String> ignoreTagNames = config.getJsonArray(COLLECTOR_IGNORE_TAGS, new JsonArray(emptyList())).stream()
                    .map(String.class::cast)
                    .collect(toSet());

            return new Builder()
                    .withTagValueReplacements(tagValueReplacements)
                    .withIgnoreMetricNamesPattern(config.getString(COLLECTOR_IGNORE_METRIC_REGEX, ""))
                    .withIgnoreMetricNames(ignoreMetricNames)
                    .withIgnoreTagNamesPattern(config.getString(COLLECTOR_IGNORE_TAGS_REGEX, ""))
                    .withIgnoreTagNames(ignoreTagNames);
        }

        public Builder withTagValueReplacements(List<Pair<String, String>> tagValueReplacements) {
            this.tagValueReplacements = tagValueReplacements;
            return this;
        }

        public Builder withIgnoreMetricNamesPattern(String ignoreMetricNamesPattern) {
            this.ignoreMetricNamesPattern = ignoreMetricNamesPattern;
            return this;
        }

        public Builder withIgnoreMetricNames(Set<String> ignoreMetricNames) {
            this.ignoreMetricNames = ignoreMetricNames;
            return this;
        }

        public Builder withIgnoreTagNamesPattern(String ignoreTagNamesPattern) {
            this.ignoreTagNamesPattern = ignoreTagNamesPattern;
            return this;
        }

        public Builder withIgnoreTagNames(Set<String> ignoreTagNames) {
            this.ignoreTagNames = ignoreTagNames;
            return this;
        }

        public PrometheusParserOptions build() {
            return new PrometheusParserOptions(tagValueReplacements, ignoreMetricNamesPattern, ignoreMetricNames, ignoreTagNamesPattern, ignoreTagNames);
        }
    }
}
