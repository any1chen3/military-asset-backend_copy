package com.military.asset.entity;

import lombok.Data;

/**
 * 省份维度的网信资产使用率统计中间结果。
 * <p>
 * 供 Mapper 聚合查询返回，包含单个上报单位在某一资产分类下
 * 的实有数量与已用数量汇总，用于后续计算使用率等指标。
 * </p>
 */
@Data
public class CyberAssetUsageAggregation {

    /** 上报单位名称 */
    private String reportUnit;

    /** 资产分类 */
    private String assetCategory;

    /** 已用数量汇总 */
    private Integer usedQuantity;

    /** 实有数量汇总 */
    private Integer actualQuantity;
}
