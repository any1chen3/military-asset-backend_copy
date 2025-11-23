package com.military.asset.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 网信资产使用率与使用年限计算工具。
 */
public final class CyberAssetUsageFormulaUtils {

    private static final int USAGE_RATE_SCALE = 4;
    private static final int USAGE_YEARS_SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final BigDecimal WARNING_THRESHOLD = BigDecimal.valueOf(5);
    private static final BigDecimal REPLACEMENT_THRESHOLD = BigDecimal.valueOf(8);

    public static final String STATUS_NORMAL = "正常";
    public static final String STATUS_WARNING = "更换预警";
    public static final String STATUS_REPLACEMENT_REQUIRED = "需要更换";

    private CyberAssetUsageFormulaUtils() {
    }

    /**
     * 计算使用率（已用数量/实有数量）。
     */
    public static BigDecimal calculateUsageRate(int usedQuantity, int actualQuantity) {
        if (actualQuantity <= 0 || usedQuantity <= 0) {
            return BigDecimal.ZERO.setScale(USAGE_RATE_SCALE, ROUNDING_MODE);
        }
        BigDecimal used = BigDecimal.valueOf(usedQuantity);
        BigDecimal actual = BigDecimal.valueOf(actualQuantity);
        return used.divide(actual, USAGE_RATE_SCALE, ROUNDING_MODE);
    }

    /**
     * 计算按数量加权的平均使用年限。
     */
    public static BigDecimal calculateWeightedUsageYears(List<UsageDurationSample> samples, LocalDate referenceDate) {
        if (samples == null || samples.isEmpty()) {
            return BigDecimal.ZERO.setScale(USAGE_YEARS_SCALE, ROUNDING_MODE);
        }
        LocalDate baseDate = Objects.requireNonNullElse(referenceDate, LocalDate.now());
        BigDecimal totalYears = BigDecimal.ZERO;
        long totalQuantity = 0;
        for (UsageDurationSample sample : samples) {
            if (sample == null || sample.quantity <= 0 || sample.putIntoUseDate == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(sample.putIntoUseDate, baseDate);
            if (days < 0) {
                continue;
            }
            BigDecimal years = BigDecimal.valueOf(days)
                    .divide(BigDecimal.valueOf(365), USAGE_YEARS_SCALE + 2, ROUNDING_MODE);
            totalYears = totalYears.add(years.multiply(BigDecimal.valueOf(sample.quantity)));
            totalQuantity += sample.quantity;
        }
        if (totalQuantity == 0) {
            return BigDecimal.ZERO.setScale(USAGE_YEARS_SCALE, ROUNDING_MODE);
        }
        return totalYears.divide(BigDecimal.valueOf(totalQuantity), USAGE_YEARS_SCALE, ROUNDING_MODE);
    }

    /**
     * 根据平均使用年限给出更换建议。
     */
    public static String determineReplacementAdvice(BigDecimal usageYears) {
        if (usageYears == null) {
            return STATUS_NORMAL;
        }
        if (usageYears.compareTo(REPLACEMENT_THRESHOLD) > 0) {
            return STATUS_REPLACEMENT_REQUIRED;
        }
        if (usageYears.compareTo(WARNING_THRESHOLD) > 0) {
            return STATUS_WARNING;
        }
        return STATUS_NORMAL;
    }

    /**
     * 计算 BigDecimal 列表的均值。
     */
    public static BigDecimal calculateMean(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO.setScale(USAGE_RATE_SCALE, ROUNDING_MODE);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            if (value != null) {
                sum = sum.add(value);
            }
        }
        return sum.divide(BigDecimal.valueOf(values.size()), USAGE_RATE_SCALE, ROUNDING_MODE);
    }

    /**
     * 计算 BigDecimal 列表的中位数。
     */
    public static BigDecimal calculateMedian(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO.setScale(USAGE_RATE_SCALE, ROUNDING_MODE);
        }
        List<BigDecimal> sorted = new ArrayList<>();
        for (BigDecimal value : values) {
            if (value != null) {
                sorted.add(value);
            }
        }
        if (sorted.isEmpty()) {
            return BigDecimal.ZERO.setScale(USAGE_RATE_SCALE, ROUNDING_MODE);
        }
        sorted.sort(Comparator.naturalOrder());
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2).setScale(USAGE_RATE_SCALE, ROUNDING_MODE);
        }
        BigDecimal low = sorted.get(size / 2 - 1);
        //BigDecimal high = sorted.get(size / 2);
        return low;
    }

    /**
     * 计算 BigDecimal 列表的方差（总体方差）。
     */
    public static BigDecimal calculateVariance(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO.setScale(USAGE_RATE_SCALE, ROUNDING_MODE);
        }
        List<BigDecimal> nonNullValues = new ArrayList<>();
        for (BigDecimal value : values) {
            if (value != null) {
                nonNullValues.add(value);
            }
        }
        if (nonNullValues.isEmpty()) {
            return BigDecimal.ZERO.setScale(USAGE_RATE_SCALE, ROUNDING_MODE);
        }
        BigDecimal mean = calculateMean(nonNullValues);
        BigDecimal sumSquares = BigDecimal.ZERO;
        for (BigDecimal value : nonNullValues) {
            BigDecimal diff = value.subtract(mean);
            sumSquares = sumSquares.add(diff.multiply(diff));
        }
        return sumSquares.divide(BigDecimal.valueOf(nonNullValues.size()), USAGE_RATE_SCALE, ROUNDING_MODE);
    }

    /**
     * 构造使用年限样本。
     */
    public static UsageDurationSample buildSample(LocalDate putIntoUseDate, int quantity) {
        return new UsageDurationSample(putIntoUseDate, Math.max(0, quantity));
    }

    /**
     * 空列表常量，避免重复创建对象。
     */
    public static List<UsageDurationSample> emptySamples() {
        return Collections.emptyList();
    }

    /**
     * 表示一条用于计算平均使用年限的样本数据。
     */
    public static final class UsageDurationSample {
        private final LocalDate putIntoUseDate;
        private final int quantity;

        public UsageDurationSample(LocalDate putIntoUseDate, int quantity) {
            this.putIntoUseDate = putIntoUseDate;
            this.quantity = quantity;
        }

        public LocalDate getPutIntoUseDate() {
            return putIntoUseDate;
        }

        public int getQuantity() {
            return quantity;
        }
    }
}
