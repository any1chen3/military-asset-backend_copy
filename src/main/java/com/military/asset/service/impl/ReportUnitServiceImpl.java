package com.military.asset.service.impl;

import com.military.asset.entity.ReportUnit;
import com.military.asset.mapper.ReportUnitMapper;
import com.military.asset.service.ReportUnitService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 上报单位服务实现类
 * 基于ReportUnit实体类的实际字段实现业务逻辑
 */
@Service
public class ReportUnitServiceImpl extends ServiceImpl<ReportUnitMapper, ReportUnit> implements ReportUnitService {

    /**
     * 获取所有不重复的上报单位名称
     * 直接从report_unit表查询，避免在三个大表中分别查询
     */
    @Override
    public List<String> getAllReportUnitNames() {
        return this.getBaseMapper().selectAllReportUnitNames();
    }

    /**
     * 根据资产表类型获取上报单位列表
     * @param tableType 表类型：software/cyber/data
     * @return 上报单位名称列表
     * 注意：这里会根据表类型查询对应的source_table_xxx_asset字段是否为1
     */
    @Override
    public List<String> getReportUnitsByTableType(String tableType) {
        return this.getBaseMapper().selectReportUnitsByTableType(tableType);
    }

    /**
     * 验证上报单位是否存在于指定资产表中
     * @param reportUnit 上报单位名称
     * @param tableType 表类型：software/cyber/data
     * @return 是否存在
     * 优化：先获取该表类型的所有上报单位，再检查是否包含目标单位
     */
    @Override
    public boolean validateReportUnitExists(String reportUnit, String tableType) {
        List<String> reportUnits = this.getReportUnitsByTableType(tableType);
        // 添加空值检查，避免NPE
        return reportUnits != null && reportUnits.contains(reportUnit);
    }

    // ==================== 新增：接口4相关方法实现 ====================

    @Override
    public List<Map<String, Object>> getProvinceUnitStats() {
        /**
         * 实现上报单位表省份单位统计（只统计有数据的单位）

         * 设计考虑：直接从report_unit表统计有数据的单位
         * 避免复杂的关联查询，提高性能

         * SQL执行逻辑：
         *   SELECT province, COUNT(*) as count
         *   FROM report_unit
         *   WHERE province IS NOT NULL
         *     AND province != ''
         *     AND (source_table_software_asset = 1
         *          OR source_table_cyber_asset = 1
         *          OR source_table_data_content_asset = 1)
         *   GROUP BY province
         *   ORDER BY count DESC

         * 与接口4(a)的关系：
         * - 4(a)分别统计三个资产表的省份分布
         * - 4(b)统计各省份有数据的单位总数（去重）
         * - 4(b)的结果应该大致等于三个资产表统计数的去重合并

         * 优势：
         * - 直接统计，性能更好
         * - 反映了各省份有数据的单位总数
         * - 避免了重复统计（一个单位在多个资产表有数据也只统计一次）
         */
        return this.getBaseMapper().selectProvinceUnitStats();
    }
}