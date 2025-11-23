package com.military.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.military.asset.entity.CyberAsset;
import com.military.asset.entity.CyberAssetUsageAggregation;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page; // 确保导入Page类
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 网信资产Mapper接口
 * 作用：定义网信资产表（cyber_asset）的所有数据库操作
 * 与Service层关系：被CyberAssetServiceImpl调用，执行实际的SQL操作
 * 与XML关系：接口方法与CyberAssetMapper.xml中的SQL语句一一对应

 * 修改说明：移除 @Mapper 注解，由 @MapperScan 统一扫描
 * 原因：Spring Boot 3.x 中 @Mapper 注解与 @MapperScan 冲突，导致 factoryBeanObjectType 错误

 * 新增功能：
 * - selectAllExistingAssets(): 查询所有完整资产对象，用于导入时关键字段比较
 */
public interface CyberAssetMapper extends BaseMapper<CyberAsset> {

    /**
     * 查询所有已存在的资产ID
     * 用途：Excel导入时校验ID是否重复，防止主键冲突
     * 调用时机：监听器初始化时调用，加载数据库中已有的ID列表
     * @return 所有资产ID组成的列表（如["id1","id2"...]）
     */
    @Select("SELECT id FROM cyber_asset")
    List<String> selectAllExistingIds();

    /**
     * 批量插入网信资产
     * 用途：Excel导入时高效保存多条数据，比单条插入效率提升10倍以上
     * 事务保证：由Service层的@Transactional注解控制，确保要么全成功要么全失败
     * @param list 待插入的网信资产实体列表（从ExcelVO转换而来）
     */
    void insertBatch(@Param("list") List<CyberAsset> list);

    /**
     * 网信基础资产联合查询方法（支持数量范围查询）

     * 功能特点：
     * - 实有数量和已用数量支持范围查询
     * - 使用动态SQL构建查询条件
     *
     * @param page MyBatis-Plus分页对象
     * @param reportUnit 上报单位
     * @param province 省份
     * @param city 城市
     * @param categoryCode 分类编码
     * @param assetCategory 资产分类
     * @param quantityMin 实有数量最小值
     * @param quantityMax 实有数量最大值
     * @param usedQuantityMin 已用数量最小值
     * @param usedQuantityMax 已用数量最大值
     * @param startUseDateStart 投入使用时间开始
     * @param startUseDateEnd 投入使用时间结束
     * @param inventoryUnit 盘点单位
     * @return 分页查询结果
     */
    Page<CyberAsset> combinedQuery(@Param("page") Page<CyberAsset> page,
                                   @Param("reportUnit") String reportUnit,
                                   @Param("province") String province,
                                   @Param("city") String city,
                                   @Param("categoryCode") String categoryCode,
                                   @Param("assetCategory") String assetCategory,
                                   @Param("quantityMin") Integer quantityMin,
                                   @Param("quantityMax") Integer quantityMax,
                                   @Param("usedQuantityMin") Integer usedQuantityMin,
                                   @Param("usedQuantityMax") Integer usedQuantityMax,
                                   @Param("startUseDateStart") String startUseDateStart,
                                   @Param("startUseDateEnd") String startUseDateEnd,
                                   @Param("inventoryUnit") String inventoryUnit);

    // ============================ 新增方法 ============================

    /**
     * 查询所有已存在的网信资产（完整对象）

     * 新增用途：用于Excel导入时关键字段比较，而不仅仅是ID重复检查
     * 核心字段：包含所有业务字段，特别是上报单位、资产分类、资产名称、资产内容等关键字段

     * 网信资产特有字段：
     * - asset_content：资产内容（网信资产特有，参与关键字段比较）
     * - used_quantity：已用数量（网信资产特有，用于业务校验）

     * 性能考虑：
     * - 一次性查询所有数据，避免多次数据库交互
     * - 数据量较大时，考虑分批加载（当前设计适用于常规数据量）
     *
     * @return 所有网信资产完整对象列表
     */
    @Select("SELECT * FROM cyber_asset")
    List<CyberAsset> selectAllExistingAssets();

    // ============================ 新增额外接口 ============================
    /**
     * 接口2：
     * 按分类编码或资产分类查询（分页）
     */
    Page<CyberAsset> queryByCategory(Page<CyberAsset> page,
                                     @Param("categoryCode") String categoryCode,
                                     @Param("assetCategory") String assetCategory);

    /**
     * 接口3
     * 按上报单位查询网信资产（分页）
     * @param page 分页对象
     * @param reportUnit 上报单位名称
     * @return 分页结果
     */
    Page<CyberAsset> queryByReportUnit(Page<CyberAsset> page, @Param("reportUnit") String reportUnit);

    // ==================== 新增：接口4相关方法 ====================

    /**
     * 接口4(a)：统计网信资产表各省份单位数量
     * 作用：按省份分组统计网信资产表中不同上报单位的数量
     * SQL逻辑与软件资产表相同，但针对cyber_asset表
     * @return 省份统计列表，每个元素包含province和count字段
     */
    List<Map<String, Object>> selectProvinceUnitStats();

    /**
     * 重置上报单位表中网信资产状态为0（清空导入专用）
     * SQL: UPDATE report_unit SET source_table_cyber_asset = 0
     *
     * @return 更新的记录数
     */
    int resetCyberAssetStatus();
    /**
     * 查询指定上报单位的全部网信资产。
     */
    List<CyberAsset> selectByReportUnit(@Param("reportUnit") String reportUnit);

    /**
     * 统计某省份下每个上报单位在各资产分类的实有/已用数量，用于计算使用率。
     */
    List<CyberAssetUsageAggregation> aggregateProvinceUsageByAssetCategory(@Param("province") String province);

    // ==================== 新增：按省份统计接口的相关方法 ====================
    /**
     * 统计各省份网信资产数量
     * 修改：将COALESCE(province, '其他')改为COALESCE(province, '未知')
     */
    @Select("SELECT COALESCE(province, '未知') as province, COUNT(*) as count " +
            "FROM cyber_asset " +
            "GROUP BY COALESCE(province, '未知')")
    List<Map<String, Object>> selectProvinceCyberStats();

    /**
     * 统计指定省份网信资产数量
     */
    @Select("SELECT COUNT(*) FROM cyber_asset WHERE COALESCE(province, '未知') = #{province}")
    Long selectCyberCountByProvince(@Param("province") String province);

    /**
     * 统计指定省份网信资产各分类数量
     * 注意：返回分类编码，由Service层转换为分类名称
     *
     * @param province 省份名称
     * @return 包含分类编码和数量的列表
     */
    @Select("SELECT asset_category, COUNT(*) as count " +
            "FROM cyber_asset " +
            "WHERE COALESCE(province, '未知') = #{province} " +
            "GROUP BY asset_category")
    List<Map<String, Object>> selectCyberCategoryStatsByProvince(@Param("province") String province);

    /**
     * 根据资产分类按省份统计网信资产数量
     * 核心逻辑：使用网信资产表自身的province字段进行统计
     * @param assetCategory 资产分类
     * @return 统计结果列表，包含province和count字段
     */
    List<Map<String, Object>> selectProvinceStatsByAssetCategory(@Param("assetCategory") String assetCategory);

    /**
     * 按上报单位汇总四类电话号码的实有数量。
     */
    List<Map<String, Object>> sumPhoneNumberQuantityByCategory(@Param("reportUnit") String reportUnit,
                                                               @Param("categories") List<String> categories);
}