package com.military.asset.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.military.asset.entity.DataContentAsset;
import com.military.asset.vo.DataAssetReportUnitAnalysisVO;
import com.military.asset.vo.ExcelErrorVO;
import com.military.asset.vo.excel.DataContentAssetExcelVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.military.asset.vo.stat.ProvinceMetricVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 数据内容资产业务层接口
 * 继承IService获取基础CRUD，新增数据特有功能（开发工具校验、按工具查询）
 * 核心约束：开发工具（developmentTool）非空
 * - getExistingAssetsMap(): 获取完整资产对象Map，用于导入时关键字段比较

 * 新增功能：
 * - 省市自动填充相关方法：支持省市字段的自动填充逻辑
 * - 上报单位同步相关方法：支持上报单位表的状态同步
 */
@SuppressWarnings("unused")
public interface DataContentAssetService extends IService<DataContentAsset> {

    // ============================ 原有方法（资产ID相关） ============================

    /**
     * 批量保存数据内容资产 - 适配AssetImportController
     * 事务保障：确保批量操作原子性（全成功/全失败）
     * @param validDataList 已校验的Excel数据VO列表
     */
    void batchSaveDataContentAssets(List<DataContentAssetExcelVO> validDataList); // 新增：适配AssetImportController

    /**
     * 查询所有已存在的数据资产ID（原有方法）
     * 用途：Excel导入去重，避免重复入库
     * @return 数字+字母组合的ID列表
     */
    List<String> getExistingIds();

    /**
     * 批量保存Excel合法数据（原有方法）
     * 事务保障：确保批量操作原子性（全成功/全失败）
     * @param validVoList 已校验的Excel数据VO列表
     */
    void batchSaveValidData(List<DataContentAssetExcelVO> validVoList);

    /**
     * 处理Excel导入结果并记录日志
     * @param totalRow 导入总行数
     * @param validRow 成功入库条数
     * @param errorList 错误详情列表
     */
    void handleImportResult(int totalRow, int validRow, List<ExcelErrorVO> errorList);

    /**
     * 按ID查询数据资产详情
     * 校验：ID格式+存在性
     * @param id 数据资产ID
     * @return 完整数据资产实体
     * @throws RuntimeException ID格式错误或不存在时抛出
     */
    DataContentAsset getById(String id);

    /**
     =============================新增========================
     * 数据内容资产联合查询方法
     * 支持多条件自由组合查询，返回分页结果和总数

     * 查询条件说明：
     * - 所有条件均为可选，可以自由组合
     * - 应用领域、开发工具、更新周期、更新方式使用固定选项
     * - 支持省份和城市的筛选
     *
     * @param pageNum 当前页码
     * @param pageSize 每页大小
     * @param reportUnit 上报单位
     * @param province 省份
     * @param city 城市
     * @param applicationField 应用领域
     * @param developmentTool 开发工具
     * @param quantityMin 实有数量最小值
     * @param quantityMax 实有数量最大值
     * @param updateCycle 更新周期
     * @param updateMethod 更新方式
     * @param inventoryUnit 盘点单位
     * @return 包含分页信息的查询结果
     */
    Object combinedQuery(Integer pageNum, Integer pageSize,
                         String reportUnit, String province, String city,
                         String applicationField, String developmentTool, Integer quantityMin, Integer quantityMax,
                         String updateCycle, String updateMethod, String inventoryUnit);



    // ============================ 原有方法（增删改操作） ============================

    /**
     * 新增数据内容资产
     * 核心流程：自动填充省市 → 数据校验 → 保存资产 → 同步上报单位表
     * 同步逻辑：使用填充后的省市信息同步上报单位表，设置数据资产状态标志为1
     *
     * @param asset 数据内容资产对象
     */
    void add(DataContentAsset asset);

    /**
     * 修改数据内容资产

     * 功能说明：
     * 根据资产ID修改数据内容资产记录，包含完整的数据校验、省市自动填充和上报单位表同步。
     * 修改成功后，创建时间会被更新为当前时间，作为最后修改时间的参考。

     * 核心特性：
     * - 完整的数据校验（与新增一致）
     * - 固定选项严格校验（盘点单位必须为"保障局"）
     * - 省市信息自动填充
     * - 上报单位变更的双向同步
     * - 创建时间更新为当前时间
     * - 事务性操作确保数据一致性
     *
     * @param asset 数据内容资产对象（包含修改后的数据和原ID）
     * @throws RuntimeException 当资产不存在、数据校验失败或更新失败时抛出业务异常
     */
    void update(DataContentAsset asset);

    /**
     * 删除数据内容资产
     * 功能说明：
     * 根据资产ID删除数据内容资产记录，并同步更新上报单位表的状态标志。
     * 使用资产中的省市信息进行同步，确保上报单位表数据的准确性。

     * 核心特性：
     * - 事务性操作，确保数据一致性
     * - 使用资产省市信息同步上报单位表
     * - 详细的日志记录和异常处理
     *
     * @param id 数据内容资产主键ID，必填参数
     * @throws RuntimeException 当资产不存在、删除失败或同步失败时抛出业务异常
     */
    void remove(String id);

    /**
     * 校验分类编码与名称匹配
     * 业务逻辑：故意使用反转调用，不匹配时抛异常，符合业务需求
     * @param categoryCode 分类编码
     * @param assetCategory 资产分类名称
     * @return 匹配返回true，否则false
     */
    @SuppressWarnings("all")
    boolean checkCategoryMatch(String categoryCode, String assetCategory);

    /**
     * 数据特有校验：开发工具非空
     * @param developmentTool 开发工具名称
     * @throws RuntimeException 开发工具为空时抛出
     */
    void validateDevelopmentTool(String developmentTool);

    /**
     * 计算指定省份的数据内容资产信息化程度。
     * 信息化程度 = 指定省份数据总量 / 全部数据总量。
     *
     * @param province 省份名称
     * @return 信息化程度占比
     */
    BigDecimal calculateProvinceInformationDegree(String province);

    /**
     * 计算指定省份的数据内容资产国产化率。
     * 国产化率 = 指定省份国产化开发工具数据总量 / 指定省份数据总量。
     *
     * @param province 省份名称
     * @return 国产化率占比
     */
    BigDecimal calculateProvinceDomesticRate(String province);

    /**
     * 批量计算各省份信息化程度（省份总量 / 全部总量）。
     *
     * <p>直接从 data_content_asset 表聚合省份数据，避免依赖上报单位表或省份表，提升查询性能。</p>
     *
     * @return 各省份信息化程度列表
     */
    List<ProvinceMetricVO> calculateAllProvinceInformationDegree();

    /**
     * 批量计算各省份国产化率（国产工具总量 / 省份总量）。
     *
     * <p>仅依赖 data_content_asset 表聚合计算，提升接口响应速度。</p>
     *
     * @return 各省份国产化率列表
     */
    List<ProvinceMetricVO> calculateAllProvinceDomesticRate();

    /**
     * 根据上报单位分析应用领域与更新周期分布，并给出职能分类、资源失衡与依赖度评估。
     *
     * @param reportUnit 上报单位
     * @return 综合分析结果
     */
    DataAssetReportUnitAnalysisVO analyzeReportUnitDomainAndCycle(String reportUnit);
    // ============================ 新增方法（资产Map获取） ============================

    /**
     * 获取所有已存在数据内容资产的完整对象Map

     * 新增用途：用于Excel导入时比较关键字段，而不仅仅是ID重复检查
     * 核心功能：
     * - 当导入数据ID与数据库重复时，比较关键字段是否一致
     * - 关键字段一致 → 静默跳过（系统重复）
     * - 关键字段不一致 → 关键错误（需修正主键）

     * 数据内容资产关键字段：上报单位、资产分类、资产名称

     * 性能优化：
     * - 一次性加载所有资产到内存，避免多次数据库查询
     * - 使用Map结构提供O(1)的查询性能
     *
     * @return Map<String, DataContentAsset> 资产ID到完整资产对象的映射
     * @throws RuntimeException 当数据加载失败时抛出
     */
    Map<String, DataContentAsset> getExistingAssetsMap();

    // ============================ 新增方法（省市自动填充相关） ============================

    /**
     * 单条新增数据内容资产（集成省市自动填充）

     * 新增功能：此方法专门用于需要省市自动填充的场景
     * 处理逻辑：
     * - 自动填充省市字段（Excel有值优先 → 部分缺失补全 → 无值则按上报单位推导）
     * - 执行完整的业务校验
     * - 同步上报单位表状态

     * 适用场景：
     * - 手动新增资产
     * - 需要自动填充省市的其他业务场景
     *
     * @param asset 待新增的数据内容资产实体
     * @throws RuntimeException 校验失败或保存失败时抛出
     */
    void addDataAsset(DataContentAsset asset);

    /**
     * 修改数据内容资产（集成省市自动填充和上报单位同步）

     * 新增功能：此方法专门处理上报单位变更时的省市重新推导
     * 特殊处理：
     * - 如果上报单位变更，强制重新推导省市
     * - 同步上报单位表（双向处理：旧单位删除逻辑 + 新单位新增逻辑）

     * 适用场景：
     * - 修改资产信息，特别是上报单位变更
     * - 需要重新推导省市的修改操作
     *
     * @param asset 含修改信息的数据内容资产实体
     * @throws RuntimeException 校验失败或更新失败时抛出
     */
    void updateDataAsset(DataContentAsset asset);

    /**
     * 删除数据内容资产（集成上报单位同步）

     * 新增功能：此方法专门处理删除时的上报单位状态检查
     * 特殊处理：
     * - 删除前获取资产信息用于同步
     * - 删除后检查该上报单位是否还有剩余数据
     * - 无剩余数据时将上报单位状态设为0

     * 适用场景：
     * - 需要同步上报单位表状态的删除操作
     *
     * @param id 待删除的资产ID
     * @throws RuntimeException ID不存在或删除失败时抛出
     */
    void deleteDataAsset(String id);

    // ============================ 新增额外接口 ============================
    /**
     * 接口1
     * 统计数据内容资产总数
     * @return 数据量
     */
    long count();

    /**
     * 接口2
     * 按应用领域查询数据内容资产（分页）
     */
    Page<DataContentAsset> queryByApplicationField(Page<DataContentAsset> page, String applicationField);

    /**
     * 接口3
     * 按上报单位查询数据内容资产（分页）
     * @param page 分页对象
     * @param reportUnit 上报单位名称
     * @return 分页结果
     */
    Page<DataContentAsset> queryByReportUnit(Page<DataContentAsset> page, String reportUnit);


// ==================== 新增：接口4相关方法 ====================

    /**
     * 接口4(a)：获取数据内容资产表各省份单位数量统计（新逻辑：关联report_unit表）
     * 作用：统计数据内容资产表中每个省份包含的不同上报单位数量
     * 新逻辑说明：
     * - 虽然data_content_asset表有province列，但为了保持三个资产表的一致性
     * - 统一通过关联report_unit表获取省份信息
     * - 确保所有资产表的省份数据都来自同一个权威来源
     * 业务逻辑：
     * - 按省份分组统计
     * - 使用DISTINCT确保同一个单位在同一个省份只统计一次
     * - 排除province为null或空字符串的记录
     * - 按单位数量降序排列
     * @return 省份统计列表，每个元素包含province(省份名称)和count(单位数量)字段
     * 示例返回：[{"province": "北京", "count": 6}, {"province": "上海", "count": 2}]
     * 注意：report_unit表的province字段经过专门维护，更加准确可靠
     */
    List<Map<String, Object>> getProvinceUnitStats();

    // ============================ 🆕 新增方法（清空再导入专用） ============================

    /**
     * 清空数据内容资产表并重置上报单位表状态（导入专用）
     * 🎯 核心操作：
     * 1. 清空data_content_asset表的所有数据
     * 2. 将report_unit表中source_table_data_content_asset字段全部设为0

     * 💡 重要说明：
     * - 只重置数据内容资产状态，不影响其他资产表的状态
     * - 不清空report_unit表的其他字段（省市信息等）
     * - 使用事务确保数据一致性

     * 🚨 风险提示：
     * - 此操作会永久删除所有数据内容资产数据
     * - 只能在导入前调用，确保数据备份
     *
     * @throws RuntimeException 当清空操作失败时抛出
     */
    void clearDataContentTableAndResetStatus();

    /**
     * 批量保存数据内容资产并同步省市信息（导入专用）

     * 🎯 与普通批量保存的区别：
     * 1. 批量处理省市信息（Excel有值优先，无值则推导）
     * 2. 批量同步上报单位表状态
     * 3. 不检查数据重复（因为表已清空）

     * 💡 数据内容资产特殊处理：
     * - 数据内容资产表有省市字段，需要特殊处理
     * - 检查Excel中的省市信息：
     *   - 如果Excel有省市：使用Excel的值
     *   - 如果Excel无省市：根据单位名称批量推导
     * - 批量更新上报单位表的省市字段和数据状态标志

     * 🔧 性能优化：
     * - 按单位名称分组，相同单位只推导一次
     * - 批量更新上报单位表，减少数据库操作
     * - 使用事务确保数据一致性
     *
     * @param assets 校验通过的数据内容资产列表
     * @throws RuntimeException 当批量保存失败时抛出
     */
    void batchSaveForImport(List<DataContentAsset> assets);

    /**
     * 数据资产联合查询方法
     * 作用：根据动态条件查询数据资产数据，支持分页
     * 参数说明：所有查询条件参数都是可选的，根据前端实际传递的条件进行查询
     * 如果所有参数都为null，则返回全部数据
     */
    Page<DataContentAsset> combinedQuery(Page<DataContentAsset> pageInfo,
                                         String reportUnit, String province, String city,
                                         String applicationField, String developmentTool, Integer quantityMin,
                                         Integer quantityMax, String updateCycle, String updateMethod,
                                         String inventoryUnit);

    /**
     * 获取各省份数据资产统计概览
     * 作用：统计34个省份+"未知"的数据资产数量和占比

     * 技术特点：
     * - 直接使用数据资产表的province字段进行统计
     * - 使用COALESCE处理null值，确保统计完整性
     * @return 包含总数量和各省份统计的结果
     */
    Map<String, Object> getProvinceAssetOverview();

    /**
     * 获取指定省份数据资产的资产分类细分
     * 作用：统计指定省份下各数据资产分类的数量和占比

     * 特殊处理：
     * - 数据内容资产只有一个分类"数据内容资产"
     * - 主要统计应用领域、开发工具等维度的细分
     *
     * @param province 省份名称，如"广东省"
     * @return 包含分类细分的统计结果
     */
    Map<String, Object> getProvinceAssetCategoryDetail(String province);

    /**
     * 接口（c）
     * 根据应用领域按省份统计数据资产数量
     * 核心逻辑：使用数据资产表自身的province字段，按应用领域统计
     * 注意：数据资产表的资产分类只有"数据内容资产"一个值，所以按应用领域统计更有意义
     * @param applicationField 应用领域（固定值，如"后勤保障"、"作战指挥"等）
     * @return 省份-数量映射，key为省份名称，value为该省份的资产数量
     */
    Map<String, Long> getProvinceStatsByApplicationField(String applicationField);
}