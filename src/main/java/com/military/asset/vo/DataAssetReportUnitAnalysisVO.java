package com.military.asset.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 数据内容资产按上报单位的领域与更新周期分析结果。
 */
@Data
public class DataAssetReportUnitAnalysisVO {

    /**
     * 上报单位名称。
     */
    private String reportUnit;

    /**
     * 应用领域计数（包含缺失领域的0填充）。
     */
    private Map<String, Long> applicationFieldCounts;

    /**
     * 应用领域占比。
     */
    private Map<String, BigDecimal> applicationFieldRatios;

    /**
     * 管理/保障/战备分类计数。
     */
    private Map<String, Long> domainCategoryCounts;

    /**
     * 管理/保障/战备分类占比。
     */
    private Map<String, BigDecimal> domainCategoryRatios;

    /**
     * 更新周期计数（包含缺失周期的0填充）。
     */
    private Map<String, Long> updateCycleCounts;

    /**
     * 更新周期占比。
     */
    private Map<String, BigDecimal> updateCycleRatios;

    /**
     * 职能分类结果（如“保障能力型”“战备水平型”“管理水平型”或“均衡型”）。
     */
    private String functionCategory;

    /**
     * 资源配置失衡提示（某类资产占比异常偏高时给出提示）。
     */
    private List<String> imbalanceWarnings;

    /**
     * 更新周期依赖得分（0-100）。
     */
    private BigDecimal dependencyScore;

    /**
     * 更新周期依赖等级（高/中/低）。
     */
    private String dependencyLevel;

    /**
     * 更新节奏倾向（高频实时型/低频管理型/静态档案型/混合型）。
     */
    private String updateRhythmTendency;
}