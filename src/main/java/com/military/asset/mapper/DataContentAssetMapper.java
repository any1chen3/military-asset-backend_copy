package com.military.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.military.asset.entity.DataContentAsset;
import com.military.asset.vo.CountVO;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page; // 确保导入Page类
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 数据内容资产Mapper接口
 * 作用：定义数据内容资产表（data_content_asset）的所有数据库操作
 * 特点：包含数据资产特有方法（如按开发工具查询）
 * 调用链：DataContentAssetServiceImpl → 本接口 → DataContentAssetMapper.xml → 数据库

 * 修改说明：移除 @Mapper 注解，由 @MapperScan 统一扫描
 * 原因：Spring Boot 3.x 中 @Mapper 注解与 @MapperScan 冲突，导致 factoryBeanObjectType 错误

 * 新增功能：
 * - selectAllExistingAssets(): 查询所有完整资产对象，用于导入时关键字段比较
 */
public interface DataContentAssetMapper extends BaseMapper<DataContentAsset> {

    /**
     * 查询所有已存在的资产ID
     * 用途：Excel导入时校验ID唯一性，防止重复入库
     * @return 资产ID列表
     */
    List<String> selectAllExistingIds();

    /**
     * 批量插入数据内容资产
     * 用途：Excel导入时高效保存数据，适合一次性导入上千条记录
     * @param list 数据内容资产实体列表
     */
    void insertBatch(@Param("list") List<DataContentAsset> list);

    /**
     * 数据内容资产联合查询方法
     * 支持多条件自由组合查询，使用MyBatis-Plus分页插件

     * 功能特点：
     * - 所有查询条件均为可选，支持自由组合
     * - 使用动态SQL构建查询条件，避免SQL注入风险
     * - 返回分页对象，包含数据列表和分页信息
     *
     * @param page MyBatis-Plus分页对象，包含分页信息
     * @param reportUnit 上报单位
     * @param province 省份
     * @param city 城市
     * @param applicationField 应用领域
     * @param developmentTool 开发工具
     * @param quantityMin 实有数量最小值（可选筛选条件，>=0）
     * @param quantityMax 实有数量最大值（可选筛选条件，>=quantityMin）
     * @param updateCycle 更新周期
     * @param updateMethod 更新方式
     * @param inventoryUnit 盘点单位
     * @return 分页查询结果，包含数据列表和分页信息
     */
    Page<DataContentAsset> combinedQuery(@Param("page") Page<DataContentAsset> page,
                                         @Param("reportUnit") String reportUnit,
                                         @Param("province") String province,
                                         @Param("city") String city,
                                         @Param("applicationField") String applicationField,
                                         @Param("developmentTool") String developmentTool,
                                         @Param("quantityMin") Integer quantityMin,
                                         @Param("quantityMax") Integer quantityMax,
                                         @Param("updateCycle") String updateCycle,
                                         @Param("updateMethod") String updateMethod,
                                         @Param("inventoryUnit") String inventoryUnit);

    // ============================ 新增方法 ============================

    /**
     * 查询所有已存在的数据内容资产（完整对象）

     * 新增用途：用于Excel导入时关键字段比较，而不仅仅是ID重复检查
     * 核心字段：包含所有业务字段，特别是上报单位、资产分类、资产名称等关键字段

     * 性能考虑：
     * - 一次性查询所有数据，避免多次数据库交互
     * - 数据量较大时，考虑分批加载（当前设计适用于常规数据量）
     *
     * @return 所有数据内容资产完整对象列表
     */
    @Select("SELECT * FROM data_content_asset")
    List<DataContentAsset> selectAllExistingAssets();

    // ============================ 新增额外接口 ============================
    /**
     * 接口2：
     * 按应用领域查询（分页）
     */
    Page<DataContentAsset> queryByApplicationField(Page<DataContentAsset> page,
                                                   @Param("applicationField") String applicationField);

    /**
     * 接口3
     * 按上报单位查询数据内容资产（分页）
     * @param page 分页对象
     * @param reportUnit 上报单位名称
     * @return 分页结果
     */
    Page<DataContentAsset> queryByReportUnit(Page<DataContentAsset> page, @Param("reportUnit") String reportUnit);

    // ==================== 新增：接口4相关方法 ====================

    /**
     * 接口4(a)：统计数据内容资产表各省份单位数量
     * 作用：按省份分组统计数据内容资产表中不同上报单位的数量
     * SQL逻辑与软件资产表相同，但针对data_content_asset表
     * @return 省份统计列表，每个元素包含province和count字段
     */
    List<Map<String, Object>> selectProvinceUnitStats();

    /**
     * 重置上报单位表中数据内容资产状态为0（清空导入专用）

     * SQL: UPDATE report_unit SET source_table_data_content_asset = 0
     *
     * @return 更新的记录数
     */
    int resetDataContentAssetStatus();

    // ==================== 新增：按省份统计接口的相关方法 ====================

    /**
     * 统计各省份数据资产数量
     * 修改：将COALESCE(province, '其他')改为COALESCE(province, '未知')
     */
    @Select("SELECT COALESCE(province, '未知') as province, COUNT(*) as count " +
            "FROM data_content_asset " +
            "GROUP BY COALESCE(province, '未知')")
    List<Map<String, Object>> selectProvinceDataContentStats();

    /**
     * 统计指定省份数据资产数量
     */
    @Select("SELECT COUNT(*) FROM data_content_asset WHERE COALESCE(province, '未知') = #{province}")
    Long selectDataContentCountByProvince(@Param("province") String province);

    /**
     * 统计指定省份数据资产各分类数量
     * 注意：返回分类编码，由Service层转换为分类名称
     *
     * @param province 省份名称
     * @return 包含分类编码和数量的列表
     */
    @Select("SELECT asset_category, COUNT(*) as count " +
            "FROM data_content_asset " +
            "WHERE COALESCE(province, '未知') = #{province} " +
            "GROUP BY asset_category")
    List<Map<String, Object>> selectDataContentCategoryStatsByProvince(@Param("province") String province);

    /**
     * 根据应用领域按省份统计数据资产数量
     * 核心逻辑：使用数据资产表自身的province字段，按应用领域统计
     * @param applicationField 应用领域
     * @return 统计结果列表，包含province和count字段
     */
    List<Map<String, Object>> selectProvinceStatsByApplicationField(@Param("applicationField") String applicationField);

    //李文灿写的------------------------------
    // 获取上报单位所在省份
    String getProvinceByReportUnit(@Param("reportUnit") String reportUnit);

    // 按省份统计应用领域的记录数
    List<CountVO> countApplicationFieldByProvince(@Param("province") String province);

    // 按省份统计开发工具的记录数
    List<CountVO> countDevelopmentToolByProvince(@Param("province") String province);

    // 按省份统计更新方式的记录数
    List<CountVO> countUpdateMethodByProvince(@Param("province") String province);

    // 统计上报单位自身的应用领域记录数
    List<CountVO> countUnitApplicationField(@Param("reportUnit") String reportUnit);

    // 统计上报单位自身的开发工具记录数
    List<CountVO> countUnitDevelopmentTool(@Param("reportUnit") String reportUnit);

    // 统计上报单位自身的更新方式记录数
    List<CountVO> countUnitUpdateMethod(@Param("reportUnit") String reportUnit);

    // 1. 按省份统计“某应用领域”的拥有单位数
    Integer countUnitsByProvinceAndAppField(
            @Param("province") String province,
            @Param("field") String field);

    /**
     * 根据上报单位分组统计应用领域数量。
     */
    List<Map<String, Object>> countApplicationFieldByReportUnit(@Param("reportUnit") String reportUnit);

    /**
     * 根据上报单位分组统计更新周期数量。
     */
    List<Map<String, Object>> countUpdateCycleByReportUnit(@Param("reportUnit") String reportUnit);

    // 2. 按省份统计“某开发工具”的拥有单位数
    Integer countUnitsByProvinceAndDevTool(
            @Param("province") String province,
            @Param("tool") String tool);

    // 3. 按省份统计“某更新方式”的拥有单位数
    Integer countUnitsByProvinceAndUpdateMethod(
            @Param("province") String province,
            @Param("method") String method);

// ==================== 新增方法 ====================

    /**
     * 根据上报单位查询所有相关记录（排除指定ID）
     *
     * @param unitName 单位名称
     * @param excludeId 要排除的记录ID
     * @return 相关记录列表（排除指定ID）
     */
    @Select("SELECT * FROM data_content_asset WHERE report_unit = #{unitName} AND id != #{excludeId} AND delete_flag = 0")
    List<DataContentAsset> selectByReportUnitExcludeId(@Param("unitName") String unitName, @Param("excludeId") String excludeId);

    /**
     * 根据上报单位统计记录数量（用于存在性检查）
     *
     * @return 记录数量
     */
    @Select("SELECT COUNT(*) FROM data_content_asset WHERE report_unit = #{reportUnit}")
    Long countByReportUnit(@Param("reportUnit") String reportUnit);

}