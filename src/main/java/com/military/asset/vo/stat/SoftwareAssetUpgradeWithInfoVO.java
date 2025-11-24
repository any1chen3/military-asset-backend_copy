package com.military.asset.vo.stat;

import lombok.Data;

import java.time.LocalDate;

/**
 * 带有升级判定结果的软件资产基础信息。
 */
@Data
public class SoftwareAssetUpgradeWithInfoVO {

    /** 资产类型（使用资产分类字段） */
    private String assetType;

    /** 资产名称 */
    private String assetName;

    /** 取得方式 */
    private String acquisitionMethod;

    /** 功能简介 */
    private String functionBrief;

    /** 部署范围 */
    private String deploymentScope;

    /** 实有数量 */
    private Integer actualQuantity;

    /** 计量单位 */
    private String unit;

    /** 服务状态 */
    private String serviceStatus;

    /** 投入使用日期 */
    private LocalDate putIntoUseDate;

    /** 是否需要升级 */
    private boolean requiresUpgrade;
}
