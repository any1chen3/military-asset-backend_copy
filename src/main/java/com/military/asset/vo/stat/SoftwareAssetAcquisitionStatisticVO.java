package com.military.asset.vo.stat;

import lombok.Data;

/**
 * 软件资产取得方式统计结果。
 */
@Data
public class SoftwareAssetAcquisitionStatisticVO {

    /** 上报单位 */
    private String reportUnit;

    /** 实有数量合计 */
    private Integer totalQuantity;

    /** 购置取得方式统计 */
    private SoftwareAssetStatisticItemVO purchase;

    /** 自主开发取得方式统计 */
    private SoftwareAssetStatisticItemVO selfDeveloped;

    /** 合作开发取得方式统计 */
    private SoftwareAssetStatisticItemVO coDeveloped;

    /** 其他取得方式统计 */
    private SoftwareAssetStatisticItemVO other;
}