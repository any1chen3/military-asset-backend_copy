package com.military.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.military.asset.entity.SoftwareAsset;
import com.military.asset.vo.stat.SoftwareAssetStatisticRow;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page; // 新增：导入Page类
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 软件资产Mapper接口
 * 定义与软件资产表交互的SQL操作，继承BaseMapper获得基础CRUD

 * 修改说明：移除 @Mapper 注解，由 @MapperScan 统一扫描
 * 原因：Spring Boot 3.x 中 @Mapper 注解与 @MapperScan 冲突，导致 factoryBeanObjectType 错误

 * 新增功能：
 * - selectAllExistingAssets(): 查询所有完整资产对象，用于导入时关键字段比较

 * 移除方法：
 * - selectAssetCategoryByCode(): 该方法未被使用，分类匹配校验通过CategoryMapUtils工具类完成
 */
public interface SoftwareAssetMapper extends BaseMapper<SoftwareAsset> {

    /**
     * 查询所有已存在的软件资产ID
     * 用途：Excel导入时校验ID是否重复，防止主键冲突
     * 调用时机：监听器初始化时调用，加载数据库中已有的ID列表
     * @return 所有资产ID组成的列表（如["id1","id2"...]）
     */
    @Select("SELECT id FROM software_asset")
    List<String> selectAllExistingIds();

    /**
     * 批量插入软件资产
     * 用途：Excel导入时高效保存多条数据，比单条插入效率提升10倍以上
     * 事务保证：由Service层的@Transactional注解控制，确保要么全成功要么全失败
     * @param list 待插入的软件资产实体列表（从ExcelVO转换而来）
     */
    void insertBatch(@Param("list") List<SoftwareAsset> list);

    /**
     * 软件资产联合查询方法（支持实有数量范围查询 + 盘点单位筛选）
     * 功能特点：
     * - 实有数量支持范围查询
     * - 支持盘点单位筛选
     * - 使用动态SQL构建查询条件
     *
     * @param page MyBatis-Plus分页对象
     * @param reportUnit 上报单位
     * @param categoryCode 分类编码
     * @param assetCategory 资产分类
     * @param acquisitionMethod 取得方式
     * @param deploymentScope 部署范围
     * @param deploymentForm 部署形式
     * @param bearingNetwork 承载网络
     * @param quantityMin 实有数量最小值
     * @param quantityMax 实有数量最大值
     * @param serviceStatus 服务状态
     * @param startUseDateStart 投入使用时间开始
     * @param startUseDateEnd 投入使用时间结束
     * @param inventoryUnit 盘点单位
     * @return 分页查询结果
     */
    Page<SoftwareAsset> combinedQuery(@Param("page") Page<SoftwareAsset> page,
                                      @Param("reportUnit") String reportUnit,
                                      @Param("categoryCode") String categoryCode,
                                      @Param("assetCategory") String assetCategory,
                                      @Param("acquisitionMethod") String acquisitionMethod,
                                      @Param("deploymentScope") String deploymentScope,
                                      @Param("deploymentForm") String deploymentForm,
                                      @Param("bearingNetwork") String bearingNetwork,
                                      @Param("quantityMin") Integer quantityMin,
                                      @Param("quantityMax") Integer quantityMax,
                                      @Param("serviceStatus") String serviceStatus,
                                      @Param("startUseDateStart") String startUseDateStart,
                                      @Param("startUseDateEnd") String startUseDateEnd,
                                      @Param("inventoryUnit") String inventoryUnit);
    // ============================ 新增方法 ============================

    /**
     * 查询所有已存在的软件资产（完整对象）

     * 新增用途：用于Excel导入时关键字段比较，而不仅仅是ID重复检查
     * 核心字段：包含所有业务字段，特别是上报单位、资产分类、资产名称等关键字段

     * 性能考虑：
     * - 一次性查询所有数据，避免多次数据库交互
     * - 数据量较大时，考虑分批加载（当前设计适用于常规数据量）
     *
     * @return 所有软件资产完整对象列表
     */
    @Select("SELECT * FROM software_asset")
    List<SoftwareAsset> selectAllExistingAssets();

    /**
     * 统计各上报单位在不同取得方式及服务状态下的数量汇总。
     *
     * @return 各单位的统计数据列表
     */
    List<SoftwareAssetStatisticRow> selectStatisticsByReportUnit();

    // ============================ 新增额外接口 ============================
    /**
     * 接口2：
     * 按分类编码或资产分类查询（分页）
     */
    Page<SoftwareAsset> queryByCategory(Page<SoftwareAsset> page,
                                        @Param("categoryCode") String categoryCode,
                                        @Param("assetCategory") String assetCategory);

    /**
     * 接口3
     * 按上报单位查询软件资产（分页）
     * @param page 分页对象
     * @param reportUnit 上报单位名称
     * @return 分页结果
     * 注意：使用@Param注解确保参数名与XML中的#{}占位符匹配
     */
    Page<SoftwareAsset> queryByReportUnit(Page<SoftwareAsset> page, @Param("reportUnit") String reportUnit);

    // ==================== 新增：接口4相关方法 ====================

    /**
     * 接口4(a)：统计软件资产表各省份单位数量
     * 作用：按省份分组统计软件资产表中不同上报单位的数量
     * SQL逻辑：
     *   SELECT province, COUNT(DISTINCT report_unit) as count
     *   FROM software_asset
     *   WHERE province IS NOT NULL AND province != ''
     *   GROUP BY province
     *   ORDER BY count DESC
     * @return 省份统计列表，每个元素包含province和count字段
     * 注意：使用DISTINCT确保同一个单位在同一个省份只统计一次
     */
    List<Map<String, Object>> selectProvinceUnitStats();

    /**
     * 重置上报单位表中软件资产状态为0（清空导入专用）
     * SQL: UPDATE report_unit SET source_table_software_asset = 0
     *
     * @return 更新的记录数
     */
    int resetSoftwareAssetStatus();

    // ==================== 新增：按省份统计接口的相关方法 ====================
    /**
     * 统计各省份软件资产数量（通过关联report_unit表）
     * 修改：将COALESCE(ru.province, '其他')改为COALESCE(ru.province, '未知')
     */
    @Select("SELECT COALESCE(ru.province, '未知') as province, COUNT(*) as count " +
            "FROM software_asset sa " +
            "LEFT JOIN report_unit ru ON sa.report_unit = ru.report_unit " +
            "GROUP BY COALESCE(ru.province, '未知')")
    List<Map<String, Object>> selectProvinceSoftwareStats();

    /**
     * 统计指定省份软件资产数量
     */
    @Select("SELECT COUNT(*) FROM software_asset sa " +
            "LEFT JOIN report_unit ru ON sa.report_unit = ru.report_unit " +
            "WHERE COALESCE(ru.province, '未知') = #{province}")
    Long selectSoftwareCountByProvince(@Param("province") String province);

    /**
     * 统计指定省份软件资产各分类数量
     * 注意：返回分类编码，由Service层转换为分类名称
     *
     * @param province 省份名称
     * @return 包含分类编码和数量的列表
     */
    @Select("SELECT asset_category, COUNT(*) as count " +
            "FROM software_asset sa " +
            "LEFT JOIN report_unit ru ON sa.report_unit = ru.report_unit " +
            "WHERE COALESCE(ru.province, '未知') = #{province} " +
            "GROUP BY asset_category")
    List<Map<String, Object>> selectSoftwareCategoryStatsByProvince(@Param("province") String province);

    /**
     * 根据资产分类按省份统计软件资产数量
     * 核心逻辑：通过关联report_unit表获取省份信息，因为软件资产表自身没有省份字段
     * @param assetCategory 资产分类
     * @return 统计结果列表，包含province和count字段
     */
    List<Map<String, Object>> selectProvinceStatsByAssetCategory(@Param("assetCategory") String assetCategory);

    /**
     * 按上报单位查询资产基础信息。
     *
     * @param reportUnit 上报单位名称
     * @return 该单位下的软件资产列表
     */
    List<SoftwareAsset> selectByReportUnitLight(@Param("reportUnit") String reportUnit);

}