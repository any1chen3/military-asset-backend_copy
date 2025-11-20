package com.military.asset.vo.stat;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 描述单个维度（取得方式或服务状态）的统计指标。
 */
@Data
public class SoftwareAssetDistributionCategoryVO {

    /**
     * 维度名称（如：购置、自主开发、在用、闲置等）。
     */
    private String name;

    /**
     * 目标上报单位在该维度下的数量总和。
     */
    private int companyTotal;

    /**
     * 目标上报单位所在省份在该维度下的数量总和。
     */
    private int provinceTotal;

    /**
     * 目标上报单位所在省份在该维度下的均值。
     */
    private BigDecimal provinceMean;

    /**
     * 目标上报单位所在省份在该维度下的中位数。
     */
    private BigDecimal provinceMedian;

    /**
     * 目标上报单位所在省份在该维度下的方差。
     */
    private BigDecimal provinceVariance;
}