package com.military.asset.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

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
    private long assetCount;

    /**
     * 综合重要性得分（0~1）。
     */
    private BigDecimal importanceScore;

    /**
     * 全表对照综合得分（0~1）。
     */
    private BigDecimal globalImportanceScore;

    /**
     * 重要性等级：高/中/低。
     */
    private String importanceLevel;

    /**
     * 分析说明。
     */
    private String advice;
    /**
     * 部署范围分布统计。
     * key：部署范围（如“军以下”“全军”），value：对应资产实有数量之和。
     */
    private Map<String, Long> deploymentScopeStats;
}