package com.military.asset.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 应用领域资产数量的统计结果。
 */
@Data
public class FieldQuantityStatisticsVO {
    /**
     * 总量（实有数量求和）。
     */
    private long total;

    /**
     * 平均数（按记录数计算）。
     */
    private BigDecimal average;

    /**
     * 中位数。
     */
    private BigDecimal median;

    /**
     * 方差。
     */
    private BigDecimal variance;
}
