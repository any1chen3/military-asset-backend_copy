package com.military.asset.vo.stat;

import lombok.Data;

import java.util.List;

/**
 * 指定上报单位的软件资产升级判定清单。
 */
@Data
public class SoftwareAssetUpgradeOverviewVO {

    /** 上报单位名称 */
    private String reportUnit;

    /**
     * 资产列表，包含判定是否需要升级的结果。
     */
    private List<SoftwareAssetUpgradeWithInfoVO> assets;
}