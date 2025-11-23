package com.military.asset.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 电话号码分类分析结果。
 */
@Data
public class PhoneNumberAnalysisVO {

    private String reportUnit;

    /**
     * 各类电话号码数量（空值会按0处理）。
     */
    private int manualCount;
    private int automaticCount;
    private int mobileCount;
    private int secrecyCount;

    private int totalCount;

    private BigDecimal serviceIndex;
    private BigDecimal mobilityIndex;
    private BigDecimal secrecyIndex;

    private BigDecimal serviceScore;
    private BigDecimal mobilityScore;
    private BigDecimal secrecyScore;

    private String type;
    private String dataFlag;
}