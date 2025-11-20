package com.military.asset.vo.stat;

import lombok.Data;

import java.time.LocalDate;

/**
 * 软件资产升级判定结果。
 */
@Data
public class SoftwareAssetUpgradeStatusVO {

    /** 软件资产ID */
    private String assetId;

    /** 软件资产名称 */
    private String assetName;

    /** 软件资产当前服务状态 */
    private String serviceStatus;

    /** 投入使用日期 */
    private LocalDate putIntoUseDate;

    /** 判定参考日期（通常为当前日期） */
    private LocalDate referenceDate;

    /** 判定阈值（单位：年） */
    private int thresholdYears;

    /** 是否需要升级 */
    private boolean requiresUpgrade;
}
