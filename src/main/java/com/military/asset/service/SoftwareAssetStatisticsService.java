package com.military.asset.service;

import com.military.asset.vo.stat.*;

import java.util.List;

/**
 * 软件资产统计服务，负责聚合取得方式与服务状态的数据并计算占比。
 */
public interface SoftwareAssetStatisticsService {

    /**
     * 查询全部上报单位的软件资产取得方式统计数据。
     *
     * @return 统计结果
     */
    List<SoftwareAssetAcquisitionStatisticVO> listAcquisitionStatistics();

    /**
     * 查询全部上报单位的软件资产服务状态统计数据。
     *
     * @return 统计结果
     */
    List<SoftwareAssetServiceStatusStatisticVO> listServiceStatusStatistics();

    /**
     * 计算全部省份的软件资产老化程度。
     *
     * @return 按省份汇总的老化统计结果列表
     */
    List<SoftwareAssetAgingStatisticVO> listProvinceAgingStatistics();
    /**␊
     * 判断某项软件资产是否需要升级。␊
     *
     * @param assetId 软件资产ID
     * @return 升级判定结果
     */
    SoftwareAssetUpgradeStatusVO determineAssetUpgradeStatus(String assetId);

    /**
     * 针对指定上报单位生成包含自主研发能力与服务状态的综合指标。
     *
     * @param reportUnit 上报单位名称
     * @return 综合指标
     */
    SoftwareAssetInsightVO buildReportUnitInsight(String reportUnit);
    /**
     * 查询指定上报单位下所有软件资产的升级判定结果。
     *
     * @param reportUnit 上报单位名称
     * @return 资产列表及判定结果
     */
    SoftwareAssetUpgradeOverviewVO listReportUnitUpgradeOverview(String reportUnit);
}