package com.military.asset.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 网信资产使用率分析汇总。
 */
@Data
public class CyberAssetUsageInsightVO {

    private String reportUnit;

    private String province;

    private List<CyberAssetCategoryUsageVO> categories;
    /**
     * 资产老化率（超过八年的数量/总数量）。
     */
    private BigDecimal agingRate;
}