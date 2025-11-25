package com.military.asset.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 提供基础统计学计算：均值、中位数、方差。
 */
public final class StatisticsCalculator {

    private static final int DEFAULT_SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private StatisticsCalculator() {
    }

    /**
     * 计算均值。
     */
    public static BigDecimal mean(List<Integer> values) {
        return mean(values, DEFAULT_SCALE);
    }

    /**
     * 计算中位数。
     */
    public static BigDecimal median(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return zero();
        }
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 1) {
            return BigDecimal.valueOf(sorted.get(size / 2)).setScale(DEFAULT_SCALE, ROUNDING_MODE);
        }
        BigDecimal low = BigDecimal.valueOf(sorted.get(size / 2 - 1));
        //BigDecimal high = BigDecimal.valueOf(sorted.get(size / 2));
        return low;
    }

    /**
     * 计算方差（总体方差）。
     */
    public static BigDecimal variance(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return zero();
        }
        BigDecimal mean = mean(values, DEFAULT_SCALE + 4);
        BigDecimal sumSquares = BigDecimal.ZERO;
        for (Integer value : values) {
            BigDecimal v = BigDecimal.valueOf(value == null ? 0 : value);
            BigDecimal diff = v.subtract(mean);
            sumSquares = sumSquares.add(diff.multiply(diff));
        }
        return sumSquares.divide(BigDecimal.valueOf(values.size()), DEFAULT_SCALE , ROUNDING_MODE);
    }

    private static BigDecimal mean(List<Integer> values, int scale) {
        if (values == null || values.isEmpty()) {
            return zero(scale);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Integer value : values) {
            if (value != null) {
                sum = sum.add(BigDecimal.valueOf(value));
            }
        }
        return sum.divide(BigDecimal.valueOf(values.size()), scale, ROUNDING_MODE)
                .setScale(scale, ROUNDING_MODE);
    }

    private static BigDecimal zero() {
        return zero(DEFAULT_SCALE );
    }

    private static BigDecimal zero(int scale) {
        return BigDecimal.ZERO.setScale(scale, ROUNDING_MODE);
    }
}