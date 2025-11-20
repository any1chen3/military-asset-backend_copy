package com.military.asset.service;

import com.military.asset.entity.ReportUnit;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;
import java.util.Map;

/**
 * 上报单位服务接口
 */
public interface ReportUnitService extends IService<ReportUnit> {

    /**
     * 获取所有不重复的上报单位名称
     */
    List<String> getAllReportUnitNames();

    /**
     * 根据资产表类型获取上报单位列表
     * @param tableType 表类型：software/cyber/data
     */
    List<String> getReportUnitsByTableType(String tableType);

    /**
     * 验证上报单位是否存在于指定资产表中
     */
    boolean validateReportUnitExists(String reportUnit, String tableType);

// ==================== 新增：接口4相关方法 ====================

    /**
     * 接口4(b)：获取上报单位表各省份单位数量统计（只统计有数据的单位）
     * 作用：统计上报单位表中各省份的单位数量，但只统计在至少一个资产表中有数据的单位
     * 新逻辑说明：
     * - 直接从report_unit表统计，不涉及关联查询
     * - 只统计source_table_software_asset、source_table_cyber_asset、source_table_data_content_asset中至少有一个为1的单位
     * - 这反映了各省份有数据的单位总数（去重后的结果）
     * 业务逻辑：
     * - 只统计在至少一个资产表中有数据的单位
     * - 按省份分组统计
     * - 排除province为null或空字符串的记录
     * - 按单位数量降序排列
     * 与接口4(a)的区别：
     * - 4(a)：从三个资产表分别统计，反映各省份在具体资产表中的分布
     * - 4(b)：从上报单位表统计，反映各省份有数据的单位总数（去重）
     * @return 省份统计列表，每个元素包含province(省份名称)和count(单位数量)字段
     * 示例返回：[{"province": "北京", "count": 8}, {"province": "上海", "count": 6}]
     * 注意：此统计反映了各省份有数据的单位总数，避免了重复统计
     */
    List<Map<String, Object>> getProvinceUnitStats();
}