package com.military.asset.mapper;

import com.military.asset.vo.stat.SoftwareAssetProvinceUsageDetail;
import com.military.asset.vo.stat.SoftwareAssetStatisticRow;

import java.util.List;

/**
 * 独立的软件资产统计Mapper，专门用于取得方式与服务状态的聚合查询。
 */
public interface SoftwareAssetStatisticsMapper {

    /**
     * 查询各上报单位的软件资产统计数据。
     *
     * @return 统计结果列表
     */
    List<SoftwareAssetStatisticRow> selectStatistics();

    /**
     * 查询指定上报单位的软件资产统计数据。
     *
     * @param reportUnit 上报单位名称
     * @return 统计结果
     */
    SoftwareAssetStatisticRow selectStatisticsByReportUnit(String reportUnit);

    /**
     * 查询一组上报单位的软件资产统计数据。
     *
     * @param reportUnits 上报单位列表
     * @return 统计结果列表
     */
    List<SoftwareAssetStatisticRow> selectStatisticsByReportUnits(List<String> reportUnits);
    /**
     * 查询全部省份的软件资产明细，用于老化统计。
     *
     * @return 明细列表
     */
    List<SoftwareAssetProvinceUsageDetail> selectAllProvinceUsageDetails();
}