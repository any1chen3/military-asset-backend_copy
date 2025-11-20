package com.military.asset.vo.stat;

import lombok.Data;

/**
 * 软件资产服务状态统计结果。
 */
@Data
public class SoftwareAssetServiceStatusStatisticVO {

    /** 上报单位 */
    private String reportUnit;

    /** 实有数量合计 */
    private Integer totalQuantity;

    /** 在用服务状态统计 */
    private SoftwareAssetStatisticItemVO inUse;

    /** 闲置服务状态统计 */
    private SoftwareAssetStatisticItemVO idle;

    /** 报废服务状态统计 */
    private SoftwareAssetStatisticItemVO scrapped;

    /** 封闭服务状态统计 */
    private SoftwareAssetStatisticItemVO closed;
}