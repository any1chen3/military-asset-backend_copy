package com.military.asset.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 省份维度的数据内容资产统计数据。
 */
@Data
public class ProvinceAssetStatisticData {

    /** 总量 */
    private long total;

    /** 平均值（总量/上报单位数量） */
    private BigDecimal average = BigDecimal.ZERO;

    /** 中位数 */
    private BigDecimal median = BigDecimal.ZERO;

    /** 方差 */
    private BigDecimal variance = BigDecimal.ZERO;
}
