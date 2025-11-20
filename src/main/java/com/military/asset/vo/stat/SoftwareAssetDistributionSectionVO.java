package com.military.asset.vo.stat;

import lombok.Data;

import java.util.List;

/**
 * 描述一组相关统计（如取得方式或服务状态）的汇总信息。
 */
@Data
public class SoftwareAssetDistributionSectionVO {

    /**
     * 维度集合对应的说明，例如“自主研发能力”或“服务状态”。
     */
    private String title;

    /**
     * 维度下的指标列表。
     */
    private List<SoftwareAssetDistributionCategoryVO> categories;
}
