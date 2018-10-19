package com.statful.converter;

import com.statful.client.CustomMetric;
import com.statful.utils.Pair;
import io.reactivex.Flowable;

import java.util.List;

public abstract class Converter {
    public abstract List<CustomMetric> convert(String text);
    public abstract List<CustomMetric> convert(String text, List<Pair<String, String>> tags);
    public abstract Flowable<CustomMetric> rxConvert(String text);
    public abstract Flowable<CustomMetric> rxConvert(String text, List<Pair<String, String>> tags);
    protected abstract CustomMetric.Builder beforeBuild(CustomMetric.Builder builder);
    protected abstract CustomMetric afterBuild(CustomMetric metrics);
    protected abstract String beforeConversion(String metrics);
    protected abstract List<CustomMetric> afterConversion(List<CustomMetric> metrics);
}
