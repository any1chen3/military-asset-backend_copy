package com.military.asset.vo.stat;

import lombok.Data;

/**
 * 软件资产在指定上报单位下的综合分析结果。
 */
@Data
public class SoftwareAssetInsightVO {

    /**
     * 上报单位名称。
     */
    private String reportUnit;

    /**
     * 上报单位所属省份（若无匹配则返回“其他”）。
     */
    private String province;

    /**
     * 自主研发能力相关统计（取得方式）。
     */
    private SoftwareAssetDistributionSectionVO acquisition;

    /**
     * 服务状态相关统计。
     */
    private SoftwareAssetDistributionSectionVO serviceStatus;
    /**
     * 针对自主研发能力的文字化评估（优秀、良好、合格、较差）。
     */
    private String rdAssessment;

    /**
     * 自主研发能力评分（0-1之间的小数，越高代表能力越强）。
     */
    private Double rdScore;

    /**
     * 针对服务状态的文字化评估（优秀、良好、合格、较差）。
     */
    private String serviceStatusAssessment;

    /**
     * 服务状态评分（0-1之间的小数，越高代表状态越好）。
     */
    private Double serviceStatusScore;

    /**
     * 针对自主研发能力的分段说明。
     */
    private String rdInsightDescription;

    /**
     * 针对服务状态的分段说明。
     */
    private String serviceStatusInsightDescription;
}