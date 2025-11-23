package com.military.asset.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.military.asset.entity.SoftwareAsset;
import com.military.asset.vo.ExcelErrorVO;
import com.military.asset.vo.ReportUnitImportanceVO;
import com.military.asset.vo.excel.SoftwareAssetExcelVO;
import com.military.asset.vo.stat.SoftwareAssetStatisticVO;
import com.military.asset.vo.SoftwareUpgradeRecommendationVO;

import java.util.List;
import java.util.Map;

/**
 * 软件资产业务层接口
 * 定义软件资产相关的所有业务操作规范，包括CRUD、Excel导入等功能
 * 继承MyBatis-Plus的IService，获得基础CRUD能力
 * - getExistingAssetsMap(): 获取完整资产对象Map，用于导入时关键字段比较

 * 新增功能：
 * - 上报单位同步相关方法：支持上报单位表的状态同步（软件资产表不需要省市字段）
 */
@SuppressWarnings("unused")
public interface SoftwareAssetService extends IService<SoftwareAsset> {


    /**
     * 查询所有已存在的数据资产ID（原有方法）
     * 用途：Excel导入去重，避免重复入库
     * @return 数字+字母组合的ID列表
     */
    List<String> getExistingIds();


    /**
     * 批量保存Excel中校验通过的合法数据
     * 特性：带事务支持，确保所有数据要么同时保存成功，要么同时失败
     * @param validDataList 经过校验的Excel数据VO列表
     */
    void batchSaveSoftwareAssets(List<SoftwareAssetExcelVO> validDataList);

    /**
     * 处理Excel导入结果并记录日志
     * 用途：审计导入情况，包括总记录数、成功数、失败数及错误详情
     * @param totalRow 导入的总记录数
     * @param validRow 校验通过并成功保存的记录数
     * @param errorList 校验失败的记录详情列表（包含行号、错误字段、原因）
     */
    void handleImportResult(int totalRow, int validRow, List<ExcelErrorVO> errorList);

    /**
     * 根据ID查询软件资产详情
     * 附加功能：包含ID存在性校验
     * @param id 资产ID（数字+字母组合，保证唯一性）
     * @return 软件资产完整信息对象
     * @throws RuntimeException 当ID不存在时抛出
     */
    SoftwareAsset getById(String id);

    /**
     * 软件资产联合查询方法（支持实有数量范围查询 + 盘点单位筛选）

     * 查询条件说明：
     * - 实有数量支持范围查询
     * - 支持盘点单位筛选
     * - 所有条件均为可选，可以自由组合
     *
     * @param pageNum 当前页码
     * @param pageSize 每页大小
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
     * @return 包含分页信息的查询结果
     */
    Object combinedQuery(Integer pageNum, Integer pageSize,
                         String reportUnit, String categoryCode, String assetCategory,
                         String acquisitionMethod, String deploymentScope, String deploymentForm,
                         String bearingNetwork, Integer quantityMin, Integer quantityMax,
                         String serviceStatus, String startUseDateStart, String startUseDateEnd,
                         String inventoryUnit);
    /**
     * 新增一条软件资产（带完整业务校验）
     * 校验项：服务状态合法性、投入使用日期有效性、分类编码与名称匹配性、ID唯一性
     * 关键逻辑：通过`!checkCategoryMatch()`判断分类不匹配，触发异常（业务需求）
     * @param asset 待新增的软件资产对象（需包含完整属性信息）
     * @throws RuntimeException 当校验失败时抛出具体错误信息
     */
    void add(SoftwareAsset asset);

    /**
     * 修改软件应用资产
     * 功能说明：
     * 根据资产ID修改软件资产记录，包含完整的数据校验和上报单位表同步。
     * 修改成功后，创建时间会被更新为当前时间，作为最后修改时间的参考。

     * 核心特性：
     * - 完整的数据校验（与新增一致）
     * - 自动金额计算
     * - 上报单位变更的双向同步
     * - 创建时间更新为当前时间
     * - 事务性操作确保数据一致性
     *
     * @param asset 软件资产对象（包含修改后的数据和原ID）
     * @throws RuntimeException 当资产不存在、数据校验失败或更新失败时抛出业务异常
     */
    void update(SoftwareAsset asset);

    /**
     * 删除软件应用资产
     * 功能说明：
     * 根据资产ID删除软件资产记录，并同步更新上报单位表的状态标志。
     * 如果该上报单位不再有软件资产，会自动将软件资产状态标志重置为0。

     * 核心特性：
     * - 事务性操作，确保数据一致性
     * - 自动同步上报单位表状态
     * - 详细的日志记录和异常处理
     *
     * @param id 软件资产主键ID，必填参数
     * @throws RuntimeException 当资产不存在、删除失败或同步失败时抛出业务异常
     */
    void remove(String id);

    /**
     * 校验分类编码与资产分类名称是否匹配
     * 匹配规则：从分类映射表中获取编码对应的标准名称，与传入名称比对
     * 调用场景：仅在`add`/`update`方法中通过"!反转"使用（不匹配时抛异常），属业务必需
     * @param categoryCode 分类编码（如"SW001"）
     * @param assetCategory 资产分类名称（如"操作系统软件"）
     * @return 匹配返回true，否则返回false
     */
    @SuppressWarnings("all")
    boolean checkCategoryMatch(String categoryCode, String assetCategory);

    /**
     * 批量保存Excel中校验通过的合法数据（原有方法）
     * 新增功能：此方法会触发上报单位表同步（软件资产表没有省市字段，所以只同步上报单位）
     * @param validVoList 经过校验的Excel数据VO列表
     */

    void batchSaveValidData(List<SoftwareAssetExcelVO> validVoList);

    // ============================ 新增方法（资产Map获取） ============================

    /**
     * 获取所有已存在软件资产的完整对象Map

     * 新增用途：用于Excel导入时比较关键字段，而不仅仅是ID重复检查
     * 核心功能：
     * - 当导入数据ID与数据库重复时，比较关键字段是否一致
     * - 关键字段一致 → 静默跳过（系统重复）
     * - 关键字段不一致 → 关键错误（需修正主键）

     * 性能优化：
     * - 一次性加载所有资产到内存，避免多次数据库查询
     * - 使用Map结构提供O(1)的查询性能
     *
     * @return Map<String, SoftwareAsset> 资产ID到完整资产对象的映射
     * @throws RuntimeException 当数据加载失败时抛出
     */
    Map<String, SoftwareAsset> getExistingAssetsMap();

    /**
     * 统计各部队单位的软件资产取得方式与服务状态占比。
     *
     * @return 统计结果列表
     */
    List<SoftwareAssetStatisticVO> statisticsByReportUnit();

    /**
     * 依据公式批量计算指定上报单位的升级必要性并生成升级建议。
     *
     * @param reportUnit 上报单位名称
     * @return 生成的升级建议结果
     */
    List<SoftwareUpgradeRecommendationVO> generateUpgradeRecommendations(String reportUnit);

    /**
     * 基于指定上报单位的软件应用资产得分按上报单位计算重要性。
     *
     * @param reportUnit 上报单位名称
     * @return 各上报单位的重要性评估
     */
    List<ReportUnitImportanceVO> analyzeReportUnitImportance(String reportUnit);

    // ============================ 新增方法（上报单位同步相关） ============================

    /**
     * 单条新增软件资产（集成上报单位同步）

     * 新增功能：此方法专门用于需要上报单位同步的场景
     * 处理逻辑：
     * - 执行完整的业务校验
     * - 同步上报单位表状态

     * 注意：软件资产表没有省市字段，所以不涉及省市自动填充

     * 适用场景：
     * - 手动新增资产
     * - 需要同步上报单位状态的其他业务场景
     *
     * @param asset 待新增的软件资产实体
     * @throws RuntimeException 校验失败或保存失败时抛出
     */
    void addSoftwareAsset(SoftwareAsset asset);

    /**
     * 修改软件资产（集成上报单位同步）

     * 新增功能：此方法专门处理上报单位变更时的同步
     * 特殊处理：
     * - 如果上报单位变更，同步上报单位表（双向处理：旧单位删除逻辑 + 新单位新增逻辑）

     * 注意：软件资产表没有省市字段，所以不涉及省市重新推导

     * 适用场景：
     * - 修改资产信息，特别是上报单位变更
     * - 需要同步上报单位表的修改操作
     *
     * @param asset 含修改信息的软件资产实体
     * @throws RuntimeException 校验失败或更新失败时抛出
     */
    void updateSoftwareAsset(SoftwareAsset asset);

    /**
     * 删除软件资产（集成上报单位同步）

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
    void deleteSoftwareAsset(String id);

    // ============================ 新增额外接口 ============================
    /**
     * 统计软件应用资产总数
     * @return 数据量
     */
    long count();

    /**
     * 按分类编码或资产分类查询软件资产（分页）
     * @param page 分页对象
     * @param categoryCode 分类编码（可选）
     * @param assetCategory 资产分类（可选）
     * @return 分页结果
     */
    Page<SoftwareAsset> queryByCategory(Page<SoftwareAsset> page, String categoryCode, String assetCategory);

    // 接口3使用：
    /**
     * 按上报单位查询软件资产（分页）
     * @param page 分页对象
     * @param reportUnit 上报单位名称
     * @return 分页结果
     */
    Page<SoftwareAsset> queryByReportUnit(Page<SoftwareAsset> page, String reportUnit);

// ==================== 新增：接口4相关方法 ====================
    /**
     * 接口4(a)：获取软件资产表各省份单位数量统计（新逻辑：关联report_unit表）
     * 作用：统计软件资产表中每个省份包含的不同上报单位数量
     * 新逻辑说明：
     * - 由于software_asset表没有province列，通过关联report_unit表获取省份信息
     * - 使用INNER JOIN将software_asset表与report_unit表关联
     * - 通过report_unit字段关联，从report_unit.province获取省份信息
     * 业务逻辑：
     * - 按省份分组统计
     * - 使用DISTINCT确保同一个单位在同一个省份只统计一次
     * - 排除province为null或空字符串的记录
     * - 按单位数量降序排列
     * @return 省份统计列表，每个元素包含province(省份名称)和count(单位数量)字段
     * 示例返回：[{"province": "北京", "count": 5}, {"province": "上海", "count": 3}]
     * 注意：省份信息来自report_unit表，确保数据准确性
     */
    List<Map<String, Object>> getProvinceUnitStats();

    // ============================ 🆕 新增方法（清空再导入专用） ============================

    /**
     * 清空软件资产表并重置上报单位表状态（导入专用）
     * 🎯 核心操作：
     * 1. 清空software_asset表的所有数据
     * 2. 将report_unit表中source_table_software_asset字段全部设为0

     * 💡 重要说明：
     * - 只重置软件资产状态，不影响其他资产表的状态
     * - 不清空report_unit表的其他字段（省市信息等）
     * - 使用事务确保数据一致性

     * 🚨 风险提示：
     * - 此操作会永久删除所有软件资产数据
     * - 只能在导入前调用，确保数据备份
     *
     * @throws RuntimeException 当清空操作失败时抛出
     */
    void clearSoftwareTableAndResetStatus();

    /**
     * 批量保存软件资产并同步省市信息（导入专用）
     * 🎯 与普通批量保存的区别：
     * 1. 批量推导省市信息（性能优化）
     * 2. 批量同步上报单位表状态
     * 3. 不检查数据重复（因为表已清空）

     * 💡 软件资产特殊处理：
     * - 软件资产表没有省市字段，所有省市信息通过上报单位表管理
     * - 根据单位名称批量推导省市信息
     * - 批量更新上报单位表的省市字段和软件状态标志

     * 🔧 性能优化：
     * - 按单位名称分组，相同单位只推导一次
     * - 批量更新上报单位表，减少数据库操作
     * - 使用事务确保数据一致性
     *
     * @param assets 校验通过的软件资产列表
     * @throws RuntimeException 当批量保存失败时抛出
     */
    void batchSaveForImport(List<SoftwareAsset> assets);

    /**
     * 软件资产联合查询方法
     * 作用：根据动态条件查询软件资产数据，支持分页
     * 参数说明：所有查询条件参数都是可选的，根据前端实际传递的条件进行查询
     * 如果所有参数都为null，则返回全部数据
     * 分页参数：pageInfo包含分页信息，pageNum和pageSize控制分页
     */
    Page<SoftwareAsset> combinedQuery(Page<SoftwareAsset> pageInfo,
                                      String reportUnit, String categoryCode, String assetCategory,
                                      String acquisitionMethod, String deploymentScope, String deploymentForm,
                                      String bearingNetwork, Integer quantityMin, Integer quantityMax,
                                      String serviceStatus, String startUseDateStart, String startUseDateEnd,
                                      String inventoryUnit);

    /**
     * 获取各省份软件资产统计概览
     * 作用：统计34个省份+"未知"的软件资产数量和占比

     * 业务逻辑：
     * 1. 通过关联report_unit表获取软件资产的省份信息
     * 2. 统计每个省份的软件资产数量
     * 3. 计算每个省份软件资产占总量的百分比
     * 4. 包含"未知"省份的统计
     * @return 包含总数量和各省份统计的Map对象
     */
    Map<String, Object> getProvinceAssetOverview();

    /**
     * 获取指定省份软件资产的资产分类细分
     * 作用：统计指定省份下各软件资产分类的数量和占比

     * 业务逻辑：
     * 1. 统计该省份软件资产总数
     * 2. 按资产分类分组统计数量
     * 3. 计算各分类在该省份中的占比
     *
     * @param province 省份名称，如"广东省"
     * @return 包含分类细分的统计结果
     */
    Map<String, Object> getProvinceAssetCategoryDetail(String province);

    /**
     * 接口6（c）
     * 根据资产分类按省份统计软件资产数量
     * 核心逻辑：通过关联report_unit表获取省份信息，因为软件资产表自身没有省份字段
     * @param assetCategory 资产分类（固定值，如"作战指挥软件"、"安全防护软件"等）
     * @return 省份-数量映射，key为省份名称，value为该省份的资产数量
     */
    Map<String, Long> getProvinceStatsByAssetCategory(String assetCategory);
}