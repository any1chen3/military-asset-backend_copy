package com.military.asset.vo;

import lombok.Data;

import java.util.List;

/**
 * 网信资产使用率分析汇总。
 */
@Data
public class CyberAssetUsageInsightVO {

    private String reportUnit;

    private String province;

    private List<CyberAssetCategoryUsageVO> categories;
}