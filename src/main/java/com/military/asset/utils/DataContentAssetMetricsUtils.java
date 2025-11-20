package com.military.asset.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 数据内容资产统计指标工具类。
 *
 * <p>封装信息化程度、国产化率等指标的通用计算逻辑，
 * 便于在服务层复用统一的公式，并确保除数为0时安全返回0。</p>
 */
public final class DataContentAssetMetricsUtils {

    private static final int DEFAULT_SCALE = 4;

    /**
     * 国产化开发工具白名单。
     *
     * <p>使用 LinkedHashSet 保持定义顺序，便于日志输出和测试断言。</p>
     */
    private static final Set<String> DOMESTIC_DEVELOPMENT_TOOLS;

    static {
        Set<String> tools = new LinkedHashSet<>();
        tools.add("达梦");
        tools.add("高斯");
        tools.add("南大通用");
        tools.add("人大金仓");
        tools.add("神州通用");
        DOMESTIC_DEVELOPMENT_TOOLS = Collections.unmodifiableSet(tools);
    }

    private DataContentAssetMetricsUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 计算省份信息化程度（省份数据总量 / 全部数据总量）。
     *
     * @param provinceTotal 指定省份的数据内容资产总量
     * @param total 全部省份的数据内容资产总量
     * @return 信息化程度占比，保留4位小数
     */
    public static BigDecimal calculateInformationDegree(long provinceTotal, long total) {
        return calculateRatio(provinceTotal, total);
    }

    /**
     * 计算省份国产化率（省份国产化开发工具数据总量 / 省份数据总量）。
     *
     * @param domesticTotal 省份使用国产化开发工具的数据内容资产总量
     * @param provinceTotal 省份的数据内容资产总量
     * @return 国产化率占比，保留4位小数
     */
    public static BigDecimal calculateDomesticRate(long domesticTotal, long provinceTotal) {
        return calculateRatio(domesticTotal, provinceTotal);
    }

    /**
     * 判断开发工具是否属于国产化工具名单。
     *
     * @param developmentTool 开发工具名称
     * @return 属于国产化工具则返回true
     */
    public static boolean isDomesticDevelopmentTool(String developmentTool) {
        if (developmentTool == null) {
            return false;
        }
        String normalized = developmentTool.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        return DOMESTIC_DEVELOPMENT_TOOLS.contains(normalized);
    }

    /**
     * 获取国产化开发工具名单（只读）。
     *
     * @return 国产化工具集合
     */
    public static Set<String> getDomesticDevelopmentTools() {
        return DOMESTIC_DEVELOPMENT_TOOLS;
    }

    private static BigDecimal calculateRatio(long numerator, long denominator) {
        if (denominator <= 0 || numerator <= 0) {
            return BigDecimal.ZERO.setScale(DEFAULT_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), DEFAULT_SCALE, RoundingMode.HALF_UP);
    }
}