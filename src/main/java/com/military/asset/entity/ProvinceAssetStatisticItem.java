package com.military.asset.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 省份下某个应用领域的统计数据。
 */
@Data
public class ProvinceAssetStatisticItem {

    /**
     * 总量（该应用领域下的资产记录数求和）。
     */
    private long total;

    /**
     * 平均值（总量 / 记录的分组数量）。
     */
    private BigDecimal average = BigDecimal.ZERO;

    /**
     * 中位数（基于记录数量列表）。
     */
    private BigDecimal median = BigDecimal.ZERO;

    /**
     * 方差（基于记录数量列表）。
     */
    private BigDecimal variance = BigDecimal.ZERO;
}
