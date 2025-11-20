package com.military.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.military.asset.entity.ReportUnit;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 上报单位表Mapper：提供查询、统计接口（供填充工具调用）
 */
public interface ReportUnitMapper extends BaseMapper<ReportUnit> {
    // 1. 根据上报单位名称查询（判断是否已存在）
    ReportUnit selectByReportUnitName(@Param("reportUnit") String reportUnit);

    // 2. 统计软件资产表中该单位的记录数（删除时判断用）
    long countSoftwareAsset(@Param("reportUnit") String reportUnit);

    // 3. 统计网信资产表中该单位的记录数
    long countCyberAsset(@Param("reportUnit") String reportUnit);

    // 4. 统计数据内容资产表中该单位的记录数
    long countDataContentAsset(@Param("reportUnit") String reportUnit);

    /**
     * 获取所有不重复的上报单位名称
     */
    @Select("SELECT DISTINCT report_unit FROM report_unit ORDER BY report_unit")
    List<String> selectAllReportUnitNames();

    // =================================新增 用于接口3=================================
    /**
     * 根据资产表类型获取上报单位列表
     * @param tableType 表类型：software/cyber/data
     */
    List<String> selectReportUnitsByTableType(@Param("tableType") String tableType);


    // ==================== 新增：接口4相关方法 ====================

    /**
     * 接口4(b)：统计上报单位表各省份单位数量（只统计有数据的单位）
     * 作用：统计上报单位表中各省份的单位数量，但只统计在至少一个资产表中有数据的单位
     * SQL逻辑：
     *   SELECT province, COUNT(*) as count
     *   FROM report_unit
     *   WHERE province IS NOT NULL
     *     AND province != ''
     *     AND (source_table_software_asset = 1
     *          OR source_table_cyber_asset = 1
     *          OR source_table_data_content_asset = 1)
     *   GROUP BY province
     *   ORDER BY count DESC
     * 与接口4(a)的区别：这里统计的是有数据的单位总数（去重）
     * @return 省份统计列表，每个元素包含province和count字段
     */
    List<Map<String, Object>> selectProvinceUnitStats();


    // 查询全部上报单位（包含省份信息）
    List<ReportUnit> selectAll();

    /**
     * 用于自动清理无效数据方法
     * 查询所有三个状态字段都为0的记录
     * @return 无效的上报单位列表
     */
    List<ReportUnit> selectAllZeroStatusUnits();

    /**
     * 查询指定上报单位所属省份。
     *
     * @param reportUnit 上报单位
     * @return 省份名称（可能为null）
     */
    String selectProvinceByReportUnit(@Param("reportUnit") String reportUnit);

    /**
     * 查询给定省份下的全部上报单位名称。
     *
     * @param province 省份名称
     * @return 上报单位列表
     */
    List<String> selectReportUnitsByProvince(@Param("province") String province);
}
