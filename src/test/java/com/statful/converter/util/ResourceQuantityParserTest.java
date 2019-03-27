package com.statful.converter.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceQuantityParserTest {

    private static Stream<Arguments> cpuParameterProvider() {
        return Stream.of(
                Arguments.arguments("100m", 100d),
                Arguments.arguments("1000m", 1000d),
                Arguments.arguments("1", 1000d),
                Arguments.arguments("2", 2000d),
                Arguments.arguments("0.5", 500d),
                Arguments.arguments(null, 0d),
                Arguments.arguments("", 0d),
                Arguments.arguments("w", 0d)
        );
    }

    private static Stream<Arguments> memParameterProvider() {
        return Stream.of(
                Arguments.arguments("1Ki", 1024d),
                Arguments.arguments("1Mi", 1048576d),
                Arguments.arguments("1Gi", 1073741824d),
                Arguments.arguments("1Ti", 1099511627776d),
                Arguments.arguments("1Pi", 1125899906842624d),
                Arguments.arguments("1Ei", 1152921504606846980d),
                Arguments.arguments("1n", 0.000000001d),
                Arguments.arguments("1u", 0.000001d),
                Arguments.arguments("1m", 0.001d),
                Arguments.arguments("1b", 1d),
                Arguments.arguments("1k", 1000d),
                Arguments.arguments("1M", 1000000d),
                Arguments.arguments("1G", 1000000000d),
                Arguments.arguments("1T", 1000000000000d),
                Arguments.arguments("1P", 1000000000000000d),
                Arguments.arguments("1E", 1000000000000000000d),
                Arguments.arguments("1000000000n", 1d),
                Arguments.arguments("1000000u", 1d),
                Arguments.arguments("1000m", 1d),
                Arguments.arguments("0.5Mi", 524288d),
                Arguments.arguments("0.5Gi", 536870912d),
                Arguments.arguments(null, 0d),
                Arguments.arguments("", 0d),
                Arguments.arguments("w", 0d)
        );
    }

    @ParameterizedTest
    @MethodSource("cpuParameterProvider")
    void parseCpuResource(String value, double expected) {
        assertEquals(expected, ResourceQuantityParser.parseCpuResource(value));
    }

    @ParameterizedTest
    @MethodSource("memParameterProvider")
    void parseMemoryResource(String value, double expected) {
        assertEquals(expected, ResourceQuantityParser.parseMemoryResource(value));
    }
}