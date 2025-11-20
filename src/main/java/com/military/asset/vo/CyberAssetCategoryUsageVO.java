package com.military.asset.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 单个资产分类的使用率分析结果。
 */
@Data
public class CyberAssetCategoryUsageVO {

    private String assetCategory;

    private Integer actualQuantity;

    private Integer usedQuantity;

    private String unit;

    /** 本单位使用率 */
    private BigDecimal usageRate;

    /** 本单位平均使用年限 */
    private BigDecimal usageYears;

    /** 更换建议 */
    private String replacementAdvice;

    /** 省份对比统计 */
    private CyberAssetUsageProvinceStatsVO provinceStats;
}
