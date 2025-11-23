package com.military.asset.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 上报单位重要性分析结果。
 */
@Data
public class ReportUnitImportanceVO {

    /**
     * 上报单位名称。
     */
    private String reportUnit;

    /**
     * 参与计算的资产数量。
     */
    private int assetCount;

    /**
     * 综合重要性得分（0~1）。
     */
    private BigDecimal importanceScore;

    /**
     * 重要性等级：高/中/低。
     */
    private String importanceLevel;

    /**
     * 分析说明。
     */
    private String advice;
}
