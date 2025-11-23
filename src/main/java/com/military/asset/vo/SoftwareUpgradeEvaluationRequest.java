package com.military.asset.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 软件升级必要性计算请求体。
 */
@Data
public class SoftwareUpgradeEvaluationRequest {
    /**
     * 软件资产ID，对应 software_asset.id
     */
    private String assetId;

    /**
     * 计算系数（0~1），为空时按0处理
     */
    private BigDecimal coefficient;

    /**
     * 安全指标（0~1）
     */
    private BigDecimal securityIndicator;

    /**
     * 性能指标（0~1）
     */
    private BigDecimal performanceIndicator;

    /**
     * 需求匹配度（0~1）
     */
    private BigDecimal requirementMatch;
}
