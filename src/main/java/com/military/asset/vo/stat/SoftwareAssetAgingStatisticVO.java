package com.military.asset.vo.stat;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 省份级软件资产老化程度统计结果。
 * <p>
 * 同时返回“在用”和“非在用”两个维度的老化率及数量，便于前端直接展示。
 * </p>
 */
@Data
public class SoftwareAssetAgingStatisticVO {

    /** 省份名称 */
    private String province;

    /** 在用资产总数量 */
    private int inUseTotalQuantity;

    /** 在用资产中需要升级的数量 */
    private int inUseUpgradeRequiredQuantity;

    /** 在用资产老化率 = 在用需要升级数量 / 在用总数量 */
    private BigDecimal inUseAgingRatio;

    /** 非在用资产总数量（含闲置、停用等状态） */
    private int notInUseTotalQuantity;

    /** 非在用资产中需要升级的数量 */
    private int notInUseUpgradeRequiredQuantity;

    /** 非在用资产老化率 = 非在用需要升级数量 / 非在用总数量 */
    private BigDecimal notInUseAgingRatio;
}