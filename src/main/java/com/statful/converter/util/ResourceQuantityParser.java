package com.statful.converter.util;

import com.statful.converter.domain.MemorySuffix;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResourceQuantityParser {

    private static final Pattern CPU_QUANTITY = Pattern.compile("([0-9.]+)(m)?");
    private static final Pattern MEMORY_QUANTITY = Pattern.compile("([0-9.]+)(Ki|Mi|Gi|Ti|Pi|Ei|n|u|m|b|k|M|G|T|P|E)$");

    public static double parseCpuResource(String cpu) {
        if (cpu == null || cpu.isEmpty()) {
            return 0;
        }

        final Matcher matcher = CPU_QUANTITY.matcher(cpu);

        if (matcher.find()) {
            final double number = Double.parseDouble(matcher.group(1));
            final String denom = matcher.group(2);

            if (denom == null || denom.isEmpty()) {
                return number * 1000; // Translate to millicores
            } else if (denom.equals("m")) {
                return number;
            }
        }

        return 0;
    }

    public static double parseMemoryResource(String memory) {
        if (memory == null || memory.isEmpty()) {
            return 0;
        }

        final Matcher matcher = MEMORY_QUANTITY.matcher(memory);

        if (matcher.find()) {
            final double number = Double.valueOf(matcher.group(1));
            return MemorySuffix.from(matcher.group(2)).toBytes(number);
        }

        return 0;
    }

}
