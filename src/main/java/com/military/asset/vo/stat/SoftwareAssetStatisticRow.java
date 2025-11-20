package com.military.asset.vo.stat;

import lombok.Data;

/**
 * Mapper查询的原始统计结果。
 * <p>
 * 该类直接映射SQL聚合查询中的字段，保存各上报单位在
 * 不同取得方式及服务状态下的数量汇总，便于业务层做
 * 百分比等进一步处理。
 */
@Data
public class SoftwareAssetStatisticRow {

    /** 上报单位 */
    private String reportUnit;

    /** 实有数量汇总 */
    private Integer totalQuantity;

    /** 购置取得方式数量 */
    private Integer purchaseQuantity;

    /** 自主开发取得方式数量 */
    private Integer selfDevelopedQuantity;

    /** 合作开发取得方式数量 */
    private Integer coDevelopedQuantity;

    /** 其他取得方式数量 */
    private Integer otherQuantity;

    /** 在用状态数量 */
    private Integer inUseQuantity;

    /** 闲置状态数量 */
    private Integer idleQuantity;

    /** 报废状态数量 */
    private Integer scrappedQuantity;

    /** 封闭状态数量 */
    private Integer closedQuantity;
}