package com.military.asset.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 省份维度的使用率统计指标。
 */
@Data
public class CyberAssetUsageProvinceStatsVO {

    /** 平均使用率 */
    private BigDecimal average;

    /** 中位数 */
    private BigDecimal median;

    /** 方差 */
    private BigDecimal variance;
}
