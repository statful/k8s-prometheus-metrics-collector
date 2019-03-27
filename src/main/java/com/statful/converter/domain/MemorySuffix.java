package com.statful.converter.domain;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;

public enum MemorySuffix {
    Ki(2, 10),
    Mi(2, 20),
    Gi(2, 30),
    Ti(2, 40),
    Pi(2, 50),
    Ei(2, 60),
    n(10, -9),
    u(10, -6),
    m(10, -3),
    b(10, 0),
    k(10, 3),
    M(10, 6),
    G(10, 9),
    T(10, 12),
    P(10, 15),
    E(10, 18);

    private static final Map<String, MemorySuffix> CONVERTER = Arrays.stream(values()).collect(Collectors.toMap(Enum::name, identity()));

    private double converter;

    MemorySuffix(long base, long exponent) {
        this.converter = Math.pow(base, exponent);
    }

    public double toBytes(double value) {
        return value * converter;
    }

    public static MemorySuffix from(String suffix) {
        return CONVERTER.getOrDefault(suffix, b);
    }
}
