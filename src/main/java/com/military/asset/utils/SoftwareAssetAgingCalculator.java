package com.military.asset.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 软件资产老化度算法工具类。
 * <p>
 * 统一封装“是否需要升级”的判断逻辑与老化比例的计算，
 * 便于在服务层复用，并确保边界值（为空、为0）时安全返回。
 * </p>
 */
public final class SoftwareAssetAgingCalculator {

    /**
     * 默认的老化阈值，单位：年。
     */
    public static final int DEFAULT_THRESHOLD_YEARS = 5;

    /**
     * 百分比或占比的默认精度。
     */
    private static final int DEFAULT_SCALE = 4;

    private SoftwareAssetAgingCalculator() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 判断软件资产是否因投入使用时间过长而需要升级。
     *
     * @param putIntoUseDate 投入使用日期
     * @param referenceDate  判定的参考日期（通常为当前日期）
     * @return {@code true} 表示距投入使用已超过指定阈值，建议升级
     */
    public static boolean requiresUpgrade(LocalDate putIntoUseDate, LocalDate referenceDate) {
        if (putIntoUseDate == null || referenceDate == null) {
            return false;
        }
        LocalDate thresholdDate = referenceDate.minusYears(DEFAULT_THRESHOLD_YEARS);
        return putIntoUseDate.isBefore(thresholdDate);
    }

    /**
     * 根据“需要升级的数量 / 总数量”计算老化比例。
     *
     * @param upgradeQuantity 需要升级的软件数量
     * @param totalQuantity   软件总数量
     * @return 老化比例，保留四位小数
     */
    public static BigDecimal calculateAgingRatio(long upgradeQuantity, long totalQuantity) {
        if (totalQuantity <= 0 || upgradeQuantity <= 0) {
            return BigDecimal.ZERO.setScale(DEFAULT_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(upgradeQuantity)
                .divide(BigDecimal.valueOf(totalQuantity), DEFAULT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 将可能为 {@code null} 的数量安全转换为非负整数。
     *
     * @param quantity 数据库中查询到的数量
     * @return 非空、非负的数量
     */
    public static int safeQuantity(Integer quantity) {
        return Math.max(0, Objects.requireNonNullElse(quantity, 0));
    }
}