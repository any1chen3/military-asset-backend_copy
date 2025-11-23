package com.military.asset.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 软件升级建议返回值对象。
 */
@Data
public class SoftwareUpgradeRecommendationVO {

    private String assetId;

    private String assetName;

    private String reportUnit;

    /**
     * 升级必要性得分
     */
    private BigDecimal necessityScore;

    /**
     * 是否需要升级（>=0.2 视为需要排期升级）
     */
    private boolean upgradeRequired;

    /**
     * 建议内容
     */
    private String recommendation;
}
