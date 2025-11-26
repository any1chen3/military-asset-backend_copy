package com.military.asset.utils;

import com.military.asset.vo.FieldQuantityStatisticsVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 省份维度下的应用领域资产数量统计工具类。
 */
public final class DataAssetProvinceStatisticsUtils {

    private static final int SCALE = 2;

    private DataAssetProvinceStatisticsUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 计算各应用领域的总量、平均数、中位数、方差。
     *
     * @param fieldQuantities key 为应用领域，value 为该领域所有记录的实有数量列表
     * @return 统计结果映射
     */
    public static Map<String, FieldQuantityStatisticsVO> calculateFieldQuantityStatistics(Map<String, List<Integer>> fieldQuantities) {
        if (fieldQuantities == null || fieldQuantities.isEmpty()) {
            return Collections.emptyMap();
        }
        return fieldQuantities.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> buildStatistics(entry.getValue())));
    }

    private static FieldQuantityStatisticsVO buildStatistics(List<Integer> quantities) {
        List<Integer> sanitized = sanitize(quantities);
        long total = sanitized.stream().mapToLong(Integer::longValue).sum();
        BigDecimal average = sanitized.isEmpty()
                ? BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(total)
                        .divide(BigDecimal.valueOf(sanitized.size()), SCALE, RoundingMode.HALF_UP);
        BigDecimal median = calculateMedian(sanitized);
        BigDecimal variance = calculateVariance(sanitized);

        FieldQuantityStatisticsVO vo = new FieldQuantityStatisticsVO();
        vo.setTotal(total);
        vo.setAverage(average);
        vo.setMedian(median);
        vo.setVariance(variance);
        return vo;
    }

    private static List<Integer> sanitize(List<Integer> quantities) {
        if (quantities == null) {
            return Collections.emptyList();
        }
        return quantities.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static BigDecimal calculateMedian(List<Integer> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        List<Integer> sorted = values.stream().sorted().collect(Collectors.toList());
        int size = sorted.size();
        if (size % 2 == 1) {
            return BigDecimal.valueOf(sorted.get(size / 2)).setScale(SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal low = BigDecimal.valueOf(sorted.get(size / 2 - 1));
        BigDecimal high = BigDecimal.valueOf(sorted.get(size / 2));
        return low.add(high)
                .divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal calculateVariance(List<Integer> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal mean = BigDecimal.valueOf(values.stream().mapToLong(Integer::longValue).sum())
                .divide(BigDecimal.valueOf(values.size()), SCALE + 2, RoundingMode.HALF_UP);
        BigDecimal sumSquares = BigDecimal.ZERO;
        for (Integer value : values) {
            BigDecimal diff = BigDecimal.valueOf(value).subtract(mean);
            sumSquares = sumSquares.add(diff.multiply(diff));
        }
        return sumSquares.divide(BigDecimal.valueOf(values.size()), SCALE, RoundingMode.HALF_UP);
    }
}
