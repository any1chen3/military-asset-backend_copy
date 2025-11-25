package com.military.asset.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据内容资产领域/更新周期分析公式工具类。
 * <p>
 * 所有算法均为纯函数，方便在Service层复用并便于单元测试。
 * </p>
 */
public final class DataAssetAnalysisFormulaUtils {

    private static final int SCALE = 4;
    private static final BigDecimal MAJOR_THRESHOLD = new BigDecimal("0.4");
    private static final BigDecimal TWO_CATEGORY_MAJOR_THRESHOLD = new BigDecimal("0.55");
    private static final BigDecimal TWO_CATEGORY_GAP_THRESHOLD = new BigDecimal("0.2");

    /**
     * 应用领域的标准顺序，用于补齐缺失字段并保持响应顺序稳定。
     */
    public static final List<String> STANDARD_APPLICATION_FIELDS = Collections.unmodifiableList(Arrays.asList(
            "建设规划", "其他", "日常办公", "战备管理", "政治工作", "装备保障", "作战指挥", "后勤保障"
    ));

    /**
     * 更新周期的标准顺序。
     */
    public static final List<String> STANDARD_UPDATE_CYCLES = Collections.unmodifiableList(Arrays.asList(
            "实时", "每天", "每月", "每季度", "每半年", "每年", "不更新", "其他"
    ));

    /**
     * 应用领域到职能分类的映射。
     */
    private static final Map<String, String> FIELD_CATEGORY_MAP;

    /**
     * 更新周期权重，用于计算依赖得分。
     */
    private static final Map<String, Double> UPDATE_CYCLE_WEIGHTS;

    static {
        Map<String, String> fieldCategory = new LinkedHashMap<>();
        fieldCategory.put("后勤保障", "保障能力");
        fieldCategory.put("装备保障", "保障能力");
        fieldCategory.put("战备管理", "战备水平");
        fieldCategory.put("作战指挥", "战备水平");
        fieldCategory.put("建设规划", "管理水平");
        fieldCategory.put("日常办公", "管理水平");
        fieldCategory.put("其他", "管理水平");
        fieldCategory.put("政治工作", "管理水平");
        FIELD_CATEGORY_MAP = Collections.unmodifiableMap(fieldCategory);

        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("实时", 1.0);
        weights.put("每天", 0.9);
        weights.put("每月", 0.6);
        weights.put("每季度", 0.5);
        weights.put("每半年", 0.4);
        weights.put("每年", 0.3);
        weights.put("不更新", 0.1);
        weights.put("其他", 0.2);
        UPDATE_CYCLE_WEIGHTS = Collections.unmodifiableMap(weights);
    }

    private DataAssetAnalysisFormulaUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 将数据库统计结果按照标准顺序补齐，缺失的键补0。
     */
    public static Map<String, Long> normalizeCountMap(List<String> standardKeys, Map<String, Long> rawCounts) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String key : standardKeys) {
            result.put(key, rawCounts.getOrDefault(key, 0L));
        }
        // 保留其他未定义但存在的条目，避免信息丢失
        rawCounts.forEach((k, v) -> result.putIfAbsent(k, v));
        return result;
    }

    /**
     * 计算占比。
     */
    public static Map<String, BigDecimal> calculateRatios(Map<String, Long> counts) {
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        if (total <= 0) {
            return counts.keySet().stream()
                    .collect(Collectors.toMap(k -> k, k -> BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP),
                            (a, b) -> a, LinkedHashMap::new));
        }
        return counts.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> BigDecimal.valueOf(e.getValue())
                                .divide(BigDecimal.valueOf(total), SCALE, RoundingMode.HALF_UP),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    /**
     * 按职能类别聚合应用领域数量。
     */
    public static Map<String, Long> aggregateByFunctionCategory(Map<String, Long> applicationFieldCounts) {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("保障能力", 0L);
        result.put("战备水平", 0L);
        result.put("管理水平", 0L);

        applicationFieldCounts.forEach((field, count) -> {
            String category = FIELD_CATEGORY_MAP.getOrDefault(field, "管理水平");
            result.compute(category, (k, v) -> v + count);
        });
        return result;
    }

    /**
     * 根据职能类别占比判定单位类型。
     * <p>
     * 兼顾上报单位仅覆盖单一/双类场景：
     * - 仅一类占比非零：直接判定为该类型。
     * - 仅两类：占比大于等于55%或领先第二名20%以上判定为主导类型，否则视为均衡。
     * - 三类及以上：沿用0.4主导阈值。
     * </p>
     */
    public static String determineFunctionCategory(Map<String, BigDecimal> categoryRatios) {
        if (categoryRatios == null || categoryRatios.isEmpty()) {
            return "均衡型";
        }

        List<Map.Entry<String, BigDecimal>> sorted = categoryRatios.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .collect(Collectors.toList());

        long nonZero = sorted.stream().filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0).count();
        if (nonZero == 0) {
            return "均衡型";
        }

        Map.Entry<String, BigDecimal> top = sorted.get(0);
        if (nonZero == 1) {
            return top.getKey() + "型";
        }

        if (nonZero == 2) {
            Map.Entry<String, BigDecimal> second = sorted.get(1);
            if (top.getValue().compareTo(TWO_CATEGORY_MAJOR_THRESHOLD) >= 0
                    || top.getValue().subtract(second.getValue()).compareTo(TWO_CATEGORY_GAP_THRESHOLD) >= 0) {
                return top.getKey() + "型";
            }
            return "均衡型";
        }

        if (top.getValue().compareTo(MAJOR_THRESHOLD) < 0) {
            return "均衡型";
        }
        return top.getKey() + "型";
    }

    /**
     * 资源配置失衡检测：当某个应用领域占比超过50%或职能类别超过65%时触发提示。
     */
    public static List<String> detectImbalance(Map<String, BigDecimal> fieldRatios, Map<String, BigDecimal> categoryRatios) {
        BigDecimal fieldThreshold = new BigDecimal("0.5");
        BigDecimal categoryThreshold = new BigDecimal("0.65");
        List<String> warnings = fieldRatios.entrySet().stream()
                .filter(e -> e.getValue().compareTo(fieldThreshold) > 0)
                .map(e -> String.format("%s 占比超过50%%，存在资源配置偏高风险", e.getKey()))
                .collect(Collectors.toList());

        warnings.addAll(categoryRatios.entrySet().stream()
                .filter(e -> e.getValue().compareTo(categoryThreshold) > 0)
                .map(e -> String.format("%s 占比超过65%%，建议关注职能资源均衡", e.getKey()))
                .collect(Collectors.toList()));
        return warnings;
    }

    /**
     * 根据更新周期计算依赖得分（0-100）。
     */
    public static BigDecimal calculateDependencyScore(Map<String, Long> updateCycleCounts) {
        long total = updateCycleCounts.values().stream().mapToLong(Long::longValue).sum();
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        double weighted = updateCycleCounts.entrySet().stream()
                .mapToDouble(e -> UPDATE_CYCLE_WEIGHTS.getOrDefault(e.getKey(), 0.2) * e.getValue())
                .sum();
        double score = (weighted / total) * 100.0;
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 根据依赖得分给出等级。
     */
    public static String determineDependencyLevel(BigDecimal score) {
        if (score == null) {
            return "低";
        }
        if (score.compareTo(new BigDecimal("75")) >= 0) {
            return "高";
        }
        if (score.compareTo(new BigDecimal("45")) >= 0) {
            return "中";
        }
        return "低";
    }

    /**
     * 根据更新周期数量判断更新节奏倾向。
     */
    public static String determineUpdateRhythm(Map<String, Long> updateCycleCounts) {
        long highFreq = updateCycleCounts.getOrDefault("实时", 0L) + updateCycleCounts.getOrDefault("每天", 0L);
        long lowFreq = updateCycleCounts.getOrDefault("每月", 0L)
                + updateCycleCounts.getOrDefault("每季度", 0L)
                + updateCycleCounts.getOrDefault("每半年", 0L)
                + updateCycleCounts.getOrDefault("每年", 0L);
        long staticArchive = updateCycleCounts.getOrDefault("不更新", 0L);

        long max = Math.max(highFreq, Math.max(lowFreq, staticArchive));
        if (max == 0) {
            return "混合型";
        }
        if (max == highFreq && highFreq > (lowFreq + staticArchive)) {
            return "高频实时型";
        }
        if (max == lowFreq && lowFreq > (highFreq + staticArchive)) {
            return "低频管理型";
        }
        if (max == staticArchive && staticArchive > (highFreq + lowFreq)) {
            return "静态档案型";
        }
        return "混合型";
    }
}
