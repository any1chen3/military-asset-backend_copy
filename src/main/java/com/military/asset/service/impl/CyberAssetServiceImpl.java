package com.military.asset.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.military.asset.entity.CyberAsset;
import com.military.asset.mapper.CyberAssetMapper;
import com.military.asset.service.CyberAssetService;
import com.military.asset.utils.CategoryMapUtils;
import com.military.asset.utils.ProvinceAutoFillTool; // 新增：导入自动填充工具
import com.baomidou.mybatisplus.extension.plugins.pagination.Page; // 确保导入Page类
import com.military.asset.vo.ExcelErrorVO;
import com.military.asset.vo.excel.CyberAssetExcelVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.math.BigDecimal;
import jakarta.annotation.Resource; // 新增：资源注入注解
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.Collections;

//导出功能依赖
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
// 添加这行导入
import org.springframework.beans.factory.annotation.Autowired;

import com.military.asset.entity.HasReportUnitAndProvince; // 🆕 新增导入
import com.military.asset.utils.AreaCacheTool; // 🆕 新增导入

//修改导入依赖
import com.military.asset.entity.DataContentAsset;
import com.military.asset.entity.ReportUnit;
import com.military.asset.mapper.DataContentAssetMapper;
import com.military.asset.mapper.ReportUnitMapper;
/**
 * 网信资产业务实现类
 * 完全遵循软件资产服务层结构，适配网信特有约束（已用数量≤实有数量）
 * 继承ServiceImpl自动获取baseMapper，无需手动注入
 * getExistingAssetsMap(): 实现完整资产对象Map的加载，用于导入时关键字段比较

 * 新增功能：
 * - 省市自动填充：集成ProvinceAutoFillTool实现省市字段自动填充
 * - 上报单位表同步：在增删改操作中同步上报单位表状态
 */
@Service
@Slf4j
@SuppressWarnings("unused")
public class CyberAssetServiceImpl extends ServiceImpl<CyberAssetMapper, CyberAsset> implements CyberAssetService {

    /**
     * 网信资产分类映射表：从工具类获取标准编码-分类对应关系
     */
    private final Map<String, String> CATEGORY_MAP = CategoryMapUtils.initCyberCategoryMap();

    /**
//     * 最大有效年限：业务规则限定投入使用日期不能早于当前76年（115修改不需要了！）
//     */
//    private static final int MAX_VALID_YEARS = 76;

    /**
     * 网信资产数据访问接口
     * 用于执行网信资产表的数据库操作，包括自定义查询和统计
     * 通过Spring依赖注入自动装配，确保单例性和线程安全
     */
    @Autowired
    private CyberAssetMapper cyberAssetMapper;

    // ============================ 新增依赖注入 ============================

    /**
     * 省市自动填充工具：负责处理省市字段的自动填充逻辑
     * 支持场景：Excel有值优先、填省补首府、填市补省、修改上报单位同步
     */
    @Resource
    private ProvinceAutoFillTool provinceAutoFillTool;

    // ==================== 依赖注入 ====================

// ==================== 依赖注入区域 ====================

    /**
     * 区域缓存工具：提供省市字典数据、首府查询、城市到省份映射等核心功能
     * 用于智能推导和标准化省市信息，确保省市数据的准确性和一致性
     */
    @Resource
    private AreaCacheTool areaCacheTool;

    /**
     * 上报单位表Mapper：操作report_unit表，用于维护上报单位的状态和省市信息
     * 提供单位查询、状态统计等核心数据库操作，支撑上报单位表的智能同步
     */
    @Resource
    private ReportUnitMapper reportUnitMapper;

    /**
     * 数据内容资产表Mapper：用于跨表同步操作
     * 当网信资产的省市变更时，同步更新数据资产表中相同单位的省市信息
     * 确保同一单位在不同资产表中的省市信息保持一致
     */
    @Resource
    private DataContentAssetMapper dataContentAssetMapper;

    // ============================ 新增方法实现 ============================

    @Override
    public Map<String, CyberAsset> getExistingAssetsMap() {
        try {
            // 查询所有已存在的网信资产（完整对象）
            List<CyberAsset> existingAssets = baseMapper.selectAllExistingAssets();

            // 转换为Map结构，键为资产ID，值为完整资产对象
            // 使用Collectors.toMap提供O(1)的查询性能
            Map<String, CyberAsset> assetsMap = existingAssets.stream()
                    .collect(Collectors.toMap(
                            CyberAsset::getId,  // 键：资产ID
                            asset -> asset,     // 值：完整资产对象
                            (existing, replacement) -> existing  // 冲突处理：保留现有值
                    ));

            log.info("成功加载{}条网信资产到内存Map，用于导入时关键字段比较", assetsMap.size());
            return assetsMap;

        } catch (Exception e) {
            log.error("加载网信资产Map失败，无法进行关键字段比较", e);
            throw new RuntimeException("加载资产数据失败: " + e.getMessage());
        }
    }

    // ============================ 原有方法实现（添加省市自动填充和上报单位同步） ============================


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveCyberAssets(List<CyberAssetExcelVO> validDataList) {
        // 调用原有的 batchSaveValidData 方法
        batchSaveValidData(validDataList);
    }

    @Override
    public List<String> getExistingIds() {
        try {
            List<String> ids = baseMapper.selectAllExistingIds();
            log.info("查询网信资产已存在ID完成，共{}条记录", ids.size());
            return ids;
        } catch (Exception e) {
            log.error("查询网信资产ID列表失败", e);
            throw new RuntimeException("查询ID失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveValidData(List<CyberAssetExcelVO> validVoList) {
        if (validVoList.isEmpty()) {
            log.info("无合法网信资产数据需保存，跳过入库");
            return;
        }

        List<CyberAsset> entities = new ArrayList<>();
        for (CyberAssetExcelVO vo : validVoList) {
            CyberAsset entity = new CyberAsset();
            BeanUtils.copyProperties(vo, entity);
            entity.setCreateTime(LocalDateTime.now());

            // ============ 新增：省市自动填充（Excel导入场景） ============
            // 调用自动填充工具，isUpdate=false表示Excel导入场景
            // 处理逻辑：Excel有值优先 → 部分缺失补全 → 无值则按上报单位推导
            provinceAutoFillTool.fillAssetProvinceCity(entity, false);
            log.debug("网信资产导入自动填充省市：ID={}, 单位={}, 省={}, 市={}",
                    entity.getId(), entity.getReportUnit(), entity.getProvince(), entity.getCity());
// ============ 测试导入 =======================   ============ ============  ============ ============ ============ ============
//            System.out.println("=== 开始处理资产 ID: " + entity.getId() + " ===");
//            System.out.println("填充前 - 省: '" + entity.getProvince() + "', 市: '" + entity.getCity() + "', 单位: '" + entity.getReportUnit() + "'");
//
//            provinceAutoFillTool.fillAssetProvinceCity(entity, false);
//
//            System.out.println("填充后 - 省: '" + entity.getProvince() + "', 市: '" + entity.getCity() + "'");
//            System.out.println("=== 处理完成，准备保存 ===");
// ============ 测试导入 ============ ============ ============ ============ ============   ============ ============ ============ ============
            // ============ 新增结束 ============
            entities.add(entity);
        }

        baseMapper.insertBatch(entities);
        log.info("网信资产批量入库成功，共{}条记录", entities.size());

        // ============ 新增：上报单位表同步（批量导入场景） ============
        // 遍历所有成功保存的实体，同步上报单位表状态
        for (CyberAsset entity : entities) {
            provinceAutoFillTool.syncReportUnit(
                    entity.getReportUnit(),  // 上报单位名称
                    entity.getProvince(),    // 填充后的省份
                    "cyber",                 // 资产类型：网信
                    false                    // isDelete=false：新增场景
            );
        }
        log.info("网信资产批量导入完成，已同步上报单位表状态");
    }

    @Override
    public void handleImportResult(int totalRow, int validRow, List<ExcelErrorVO> errorList) {
        log.info("==== 网信资产Excel导入结果 ====");
        log.info("总记录数：{} | 成功入库：{}条 | 校验失败：{}条", totalRow, validRow, errorList.size());
        if (!errorList.isEmpty()) {
            log.warn("导入错误详情：");
            errorList.forEach(error ->
                    log.warn("行号：{} | 错误字段：{} | 原因：{}",
                            error.getExcelRowNum(), error.getErrorFields(), error.getErrorMsg())
            );
        }
    }

    @Override
    public CyberAsset getById(String id) {
        // 移除32位长度限制，只检查非空和格式
        if (!StringUtils.hasText(id) || !isValidAssetId(id)) {
            throw new RuntimeException("网信资产ID格式错误，必须由字母和数字组成");
        }

        CyberAsset asset = baseMapper.selectById(id);
        if (asset == null) {
            throw new RuntimeException("未找到ID为" + id + "的网信资产");
        }
        log.info("查询网信资产详情成功，ID：{}", id);
        return asset;
    }

    // ====================== 网信基础资产联合查询方法实现（支持数量范围查询） ======================
    @Override
    public Object combinedQuery(Integer pageNum, Integer pageSize,
                                String reportUnit, String province, String city,
                                String categoryCode, String assetCategory,
                                Integer quantityMin, Integer quantityMax,
                                Integer usedQuantityMin, Integer usedQuantityMax,
                                String startUseDateStart, String startUseDateEnd, String inventoryUnit) {
        try {
            log.info("执行网信基础资产联合查询：pageNum={}, pageSize={}, reportUnit={}, province={}, city={}, " +
                            "categoryCode={}, assetCategory={}, quantityMin={}, quantityMax={}, usedQuantityMin={}, usedQuantityMax={}, " +
                            "startUseDateStart={}, startUseDateEnd={}, inventoryUnit={}",
                    pageNum, pageSize, reportUnit, province, city, categoryCode,
                    assetCategory, quantityMin, quantityMax, usedQuantityMin, usedQuantityMax,
                    startUseDateStart, startUseDateEnd, inventoryUnit);

            // 创建分页对象，使用MyBatis-Plus的分页功能
            Page<CyberAsset> page = new Page<>(pageNum, pageSize);

            // 调用Mapper进行联合查询
            Page<CyberAsset> resultPage = baseMapper.combinedQuery(
                    page, reportUnit, province, city, categoryCode,
                    assetCategory, quantityMin, quantityMax, usedQuantityMin, usedQuantityMax,
                    startUseDateStart, startUseDateEnd, inventoryUnit
            );

            log.info("网信基础资产联合查询完成，共查询到{}条记录，分{}页显示",
                    resultPage.getTotal(), resultPage.getPages());
            return resultPage;

        } catch (Exception e) {
            log.error("网信基础资产联合查询执行失败", e);
            throw new RuntimeException("联合查询执行失败: " + e.getMessage());
        }
    }

    /**
     * 新增网信基础资产（集成上报单位表同步）
     * 功能概述：
     * 本方法用于新增单条网信资产记录，包含完整的数据校验、业务处理、数据保存和上报单位表同步功能。
     * 网信资产表与其他资产表的主要区别：有省市字段，需要同时维护自身省市字段和上报单位表。
     * 核心流程：
     * 1. 智能省市处理阶段 → 2. 数据校验阶段 → 3. 数据处理阶段 → 4. 数据保存阶段 → 5. 上报单位表同步阶段 → 6. 级联更新阶段 → 7. 跨表同步阶段

     * ==================== 新增场景处理逻辑 ====================
     *
     * 🎯 场景A：用户输入了有效省市
     *   - 用户输入"江苏省-南京市"（有效且一致） → 直接使用用户输入
     *   - 用户输入"江苏省-广州市"（有效但不一致） → "江苏省-未知"（市设为未知，省保留）
     *   - 用户输入"江苏省-无效市"（省有效市无效） → "江苏省-未知"
     *   - 用户输入"无效省-南京市"（省无效市有效） → "江苏省-南京市"（根据市推导省）
     *
     * 🎯 场景B：用户输入无效，上报单位表存在有效省份
     *   - 单位表有"浙江省"（省份有效） → "浙江省-杭州市"（使用省份+推导城市）
     *   - 单位表有"未知"（省份无效） → 进入场景C
     *
     * 🎯 场景C：用户输入无效，上报单位表不存在或省份无效
     *   - 单位不存在 → 智能推导完整省市
     *   - 单位存在但省份无效 → 智能推导完整省市
     *   - 推导失败 → "未知-未知"
     *
     * 🎯 场景D：级联更新触发
     *   - 单位在本表已存在其他记录 → 更新所有相同单位记录为最终省市
     *   - 单位在本表无其他记录 → 跳过级联更新
     *
     * 🎯 场景E：跨表同步触发
     *   - 单位在数据内容资产表存在记录 → 同步省市信息到数据内容资产表
     *   - 单位在数据内容资产表不存在记录 → 跳过跨表同步
     *
     * ==================== 同步机制说明 ====================
     * 1. 本表级联更新：确保同一单位在cyber_asset表中省市一致
     * 2. 上报单位表同步：更新单位表省份信息和cyber状态位
     * 3. 跨表同步：确保相同单位在data_content_asset表中省市一致
     *
     * 数据校验规则（按字段顺序）：
     * 1.1 主键：必填，数字字母组合，确保唯一性
     * 1.2 上报单位：必填字段
     * 1.3 分类编码与资产分类：必填，严格匹配预设映射关系
     * 1.4 资产名称：必填字段
     * 1.5 资产内容：必填字段，直接写出资产内容的信息
     * 1.6 实有数量：必填，非负整数（支持0）
     * 1.7 计量单位：必填字段
     * 1.8 单价：可选字段，如果填写则必须为非负数
     * 1.9 投入使用日期：必填，必须≥1949-10-01且≤当前日期
     * 1.10 已用数量：必填，非负整数且≤实有数量
     * 1.11 盘点单位：必填字段

     * 特殊处理逻辑：
     * - 省市智能处理：基于用户输入、单位表信息、智能推导的完整场景覆盖
     * - 级联更新：确保同一单位在本表中所有记录省市一致
     * - 跨表同步：确保相同单位在数据内容资产表中省市一致
     * - 金额字段：如果金额为空，且单价和实有数量都存在，则自动计算金额（单价×数量）
     * - 创建时间：系统自动生成当前时间
     * - 上报单位同步：使用填充后的省市信息同步到上报单位表
     *
     * 事务管理：
     * - 使用@Transactional注解确保操作原子性
     * - 任何校验失败或保存失败都会回滚整个事务
     *
     * 适用场景：
     * - 前端手动新增网信资产
     * - 需要完整校验和上报单位同步的业务场景
     * - 单条记录新增操作
     *
     * 注意事项：
     * - 资产内容需要直接写出资产内容的信息，如IP地址范围段、频谱范围段等
     * - 已用数量必须小于等于实有数量
     * - 分类编码与资产分类必须严格匹配预设映射，否则校验失败
     * - 投入使用日期有严格的时间范围限制（1949年至今）
     * - 金额计算尊重用户输入，仅在金额为空时自动计算
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(CyberAsset asset) {
        log.info("开始新增网信资产，ID：{}", asset.getId());

        // ==================== 1. 智能省市处理阶段 ====================
        log.debug("🌍 阶段1：开始智能省市处理");

        // 🎯 场景A/B/C：智能省市处理（完整场景覆盖）
        handleProvinceCityForAdd(asset);
        log.debug("✅ 智能省市处理完成 - 省份：{}，城市：{}", asset.getProvince(), asset.getCity());

        // ==================== 2. 数据校验阶段 ====================
        log.debug("📋 阶段2：开始数据校验");

        // 2.1 主键校验：必填，数字字母组合，确保唯一
        validatePrimaryKey(asset);

        // 2.2 上报单位校验：必填
        validateReportUnit(asset);

        // 2.3 分类编码与资产分类校验：必填，严格匹配
        validateCategory(asset);

        // 2.4 资产名称校验：必填
        validateAssetName(asset);

        // 2.5 资产内容校验：必填
        validateAssetContent(asset);

        // 2.6 实有数量校验：必填，非负整数
        validateActualQuantity(asset);

        // 2.7 计量单位校验：必填
        validateUnit(asset);

        // 2.8 单价校验：可选，如果填写则必须非负
        validateUnitPrice(asset);

        // 2.9 投入使用日期校验：必填，≥1949-10-01且≤当前日期
        validatePutIntoUseDate(asset);

        // 2.10 已用数量校验：必填，非负整数且≤实有数量
        validateUsedQuantity(asset);

        // 2.11 盘点单位校验：必填
        validateInventoryUnit(asset);

        // ==================== 3. 数据处理阶段 ====================
        log.debug("💰 阶段3：开始数据处理");

        // 3.1 系统自动生成创建时间
        asset.setCreateTime(LocalDateTime.now());

        // 3.2 计算金额（如果金额为空，且有单价和数量，则自动计算）
        calculateAmount(asset);

        // ==================== 4. 数据保存阶段 ====================
        log.debug("💾 阶段4：开始数据保存");

        baseMapper.insert(asset);
        log.info("新增网信资产成功，ID：{}，资产名称：{}", asset.getId(), asset.getAssetName());

        // ==================== 5. 上报单位表同步阶段 ====================
        log.debug("🔄 阶段5：开始上报单位表同步");

        // 5.1 上报单位表同步（单条新增场景）
        // 使用填充后的省市信息同步上报单位表，设置网信资产状态标志为1
        provinceAutoFillTool.syncReportUnit(
                asset.getReportUnit(),  // 上报单位名称
                asset.getProvince(),    // 网信资产有省份字段，使用填充后的省份
                "cyber",                // 资产类型：网信
                false                   // isDelete=false：新增场景
        );
        log.debug("✅ 上报单位表同步完成");

        // ==================== 6. 级联更新阶段 ====================
        log.debug("🔄 阶段6：开始级联更新检查");

        // 🎯 场景D：级联更新处理
        // 如果单位在本表已存在其他记录，更新这些记录的省市信息
        handleCascadeUpdateForAdd(asset, asset.getReportUnit());

        // ==================== 7. 跨表同步阶段 ====================
        log.debug("🔄 阶段7：开始跨表同步决策");

        // 🎯 场景E：跨表同步处理
        // 如果单位在数据内容资产表存在记录，同步省市信息到数据内容资产表
        handleCrossSyncForAdd(asset);

        log.info("🎉 网信资产新增完整流程完成，ID：{}", asset.getId());
    }

// ==================== 新增的智能省市处理方法 ====================

    /**
     * 🎯 新增时的智能省市处理（完整场景覆盖）
     *
     * ==================== 场景决策流程图 ====================
     *
     * 开始
     *   ↓
     * 检查用户输入有效性
     *   ↓
     * 用户输入有效? ──是──→ 场景A：使用用户输入
     *   ↓ 否
     * 查询上报单位表
     *   ↓
     * 单位存在且省份有效? ──是──→ 场景B：使用单位表省份+推导城市
     *   ↓ 否
     * 场景C：智能推导完整省市
     *   ↓
     * 统一有效性处理（确保最终省市有效）
     *   ↓
     * 结束
     *
     * @param asset 新增资产对象
     */
    private void handleProvinceCityForAdd(CyberAsset asset) {
        String userProvince = asset.getProvince();
        String userCity = asset.getCity();
        String reportUnit = asset.getReportUnit();

        log.info("🤖 网信资产新增智能省市处理 - 单位: {}, 用户输入: {}-{}",
                reportUnit, userProvince, userCity);

        // 🎯 场景A：用户输入了有效省市 → 使用用户输入（最高优先级）
        if (isProvinceValid(userProvince) && isCityValid(userCity) &&
                isProvinceCityConsistent(userProvince, userCity)) {

            log.info("🎯 场景A：用户输入有效省市，使用用户输入");
            standardizeProvinceCity(asset);

        } else {
            // 🎯 查询上报单位表
            ReportUnit existingUnit = reportUnitMapper.selectByReportUnitName(reportUnit);

            if (existingUnit != null && isProvinceValid(existingUnit.getProvince())) {
                // 🎯 场景B：上报单位存在且省份有效 → 使用单位表省份，推导城市
                log.info("🎯 场景B：上报单位存在且省份有效，使用单位表省份");
                useReportUnitProvince(asset, existingUnit);

            } else {
                // 🎯 场景C：单位不存在或省份无效 → 智能推导
                log.info("🎯 场景C：单位不存在或省份无效，进行智能推导");
                useToolToDeriveProvinceCity(asset, reportUnit);
            }
        }

        log.info("✅ 网信资产新增省市处理完成 - 最终: {}-{}", asset.getProvince(), asset.getCity());
    }

    /**
     * 🎯 使用上报单位表的省份信息
     *
     * @param asset 资产对象
     * @param reportUnit 上报单位信息
     */
    private void useReportUnitProvince(CyberAsset asset, ReportUnit reportUnit) {
        String unitProvince = reportUnit.getProvince();

        log.debug("📋 使用单位表省份信息：{}", unitProvince);

        // 使用单位表的省份
        asset.setProvince(unitProvince);

        // 根据省份推导城市（使用省份首府）
        try {
            String capital = areaCacheTool.getCapitalByProvinceName(unitProvince);
            if (StringUtils.hasText(capital) && !"未知".equals(capital)) {
                asset.setCity(capital);
                log.debug("✅ 根据省份推导城市成功: {} → {}", unitProvince, capital);
            } else {
                asset.setCity("未知");
                log.warn("⚠️ 无法获取省份首府，城市设为未知: {}", unitProvince);
            }
        } catch (Exception e) {
            log.error("❌ 获取首府失败，城市设为未知: {}", unitProvince, e);
            asset.setCity("未知");
        }
    }

// ==================== 新增的级联更新方法 ====================

    /**
     * 🎯 新增时的级联更新处理
     *
     * ==================== 场景D详细说明 ====================
     *
     * 🎯 触发条件：
     *   - 该单位在 cyber_asset 表中已存在其他记录
     *   - 新增记录的最终省市与现有记录不一致
     *
     * 🎯 不触发条件：
     *   - 单位在本表无其他记录（首次使用该单位）
     *   - 所有现有记录省市与新增记录一致
     *
     * @param asset 新增资产对象（包含最终省市信息）
     * @param unitName 单位名称
     */
    private void handleCascadeUpdateForAdd(CyberAsset asset, String unitName) {
        if (!StringUtils.hasText(unitName)) {
            log.debug("⏭️ 场景D跳过：单位名称为空");
            return;
        }

        try {
            // 查询该单位在本表的所有记录
            List<CyberAsset> sameUnitAssets = baseMapper.selectByReportUnitExcludeId(unitName, asset.getId());


            if (sameUnitAssets == null || sameUnitAssets.isEmpty()) {
                log.debug("⏭️ 场景D跳过：单位[{}]在本表无其他记录", unitName);
                return;
            }

            String finalProvince = asset.getProvince();
            String finalCity = asset.getCity();

            log.info("🎯 场景D触发：单位[{}]在本表存在{}条记录，开始级联更新",
                    unitName, sameUnitAssets.size());

            int updatedCount = 0;
            int skippedCount = 0;

            for (CyberAsset existingAsset : sameUnitAssets) {
                // 检查是否需要更新
                boolean needsUpdate = !Objects.equals(existingAsset.getProvince(), finalProvince) ||
                        !Objects.equals(existingAsset.getCity(), finalCity);

                if (needsUpdate) {
                    // 记录原始值
                    String oldProvince = existingAsset.getProvince();
                    String oldCity = existingAsset.getCity();

                    // 更新记录 - 使用创建时间作为更新时间
                    existingAsset.setProvince(finalProvince);
                    existingAsset.setCity(finalCity);
                    existingAsset.setCreateTime(LocalDateTime.now()); // 使用创建时间作为更新时间

                    int count = baseMapper.updateById(existingAsset);
                    if (count > 0) {
                        updatedCount++;
                        log.info("✅ 场景D更新成功：记录[{}] {}-{} → {}-{}",
                                existingAsset.getId(), oldProvince, oldCity, finalProvince, finalCity);
                    } else {
                        log.warn("⚠️ 场景D更新失败：记录[{}]", existingAsset.getId());
                    }
                } else {
                    skippedCount++;
                    log.debug("⏭️ 场景D跳过：记录[{}]省市一致，无需更新", existingAsset.getId());
                }
            }

            log.info("✅ 场景D完成：成功更新{}/{}条记录，跳过{}条记录",
                    updatedCount, sameUnitAssets.size(), skippedCount);

        } catch (Exception e) {
            log.error("❌ 场景D失败：级联更新异常，单位[{}]", unitName, e);
        }
    }

// ==================== 新增的跨表同步方法 ====================

    /**
     * 🎯 新增时的跨表同步决策
     *
     * ==================== 场景E详细说明 ====================
     *
     * 🎯 触发条件：
     * 1. 单位名称不为空
     * 2. 单位在数据内容资产表中存在且状态为1
     *
     * 🆕 不再检查省市是否为"未知"，无条件同步
     *
     * @param asset 新增资产对象
     */
    private void handleCrossSyncForAdd(CyberAsset asset) {
        String unitName = asset.getReportUnit();
        String province = asset.getProvince();
        String city = asset.getCity();

        // 检查基本条件
        if (!StringUtils.hasText(unitName)) {
            log.debug("⏭️ 场景E跳过：单位名称为空");
            return;
        }

        try {
            // 🎯 关键条件：检查单位在数据内容资产表中是否存在且状态为1
            boolean unitExistsInDataContentTable = checkUnitExistsInDataContentTable(unitName);
            if (!unitExistsInDataContentTable) {
                log.debug("⏭️ 场景E跳过：单位[{}]在数据内容资产表中不存在", unitName);
                return;
            }

            log.info("🎯 场景E触发：单位[{}]在数据内容资产表存在，开始跨表同步 → {}-{}", unitName, province, city);
            syncToDataTable(unitName, province, city);
            log.info("✅ 场景E完成：跨表同步成功");

        } catch (Exception e) {
            log.error("❌ 场景E失败：跨表同步异常，单位[{}]", unitName, e);
        }
    }

    /**
     * 🎯 检查单位在数据内容资产表中是否存在
     *
     * @param unitName 单位名称
     * @return 是否存在（true：存在，false：不存在或检查失败）
     */
    private boolean checkUnitExistsInDataContentTable(String unitName) {
        try {
            // 使用数据内容资产Mapper查询该单位是否存在记录
            Long count = dataContentAssetMapper.countByReportUnit(unitName);
            boolean exists = count != null && count > 0;
            log.debug("🔍 单位存在性检查：[{}]在数据内容资产表中{}存在", unitName, exists ? "" : "不");
            return exists;
        } catch (Exception e) {
            log.error("❌ 单位存在性检查失败，单位[{}]", unitName, e);
            return false;
        }
    }


// ==================== 详细的校验方法 ====================

    /**
     * 2.1 主键校验
     * 规则：必填，唯一标识，数字字母组合，确保在组内唯一且不与之前组别冲突
     */
    private void validatePrimaryKey(CyberAsset asset) {
        if (!StringUtils.hasText(asset.getId())) {
            throw new RuntimeException("主键不能为空");
        }

        // 数字字母组合校验
        if (!isValidAssetId(asset.getId())) {
            throw new RuntimeException("主键格式错误，必须由字母和数字组成");
        }

        // 唯一性校验
        if (getExistingIds().contains(asset.getId())) {
            throw new RuntimeException("主键已存在：" + asset.getId() + "，请更换ID");
        }
    }

    /**
     * 2.2 上报单位校验
     * 规则：必填
     */
    private void validateReportUnit(CyberAsset asset) {
        if (!StringUtils.hasText(asset.getReportUnit())) {
            throw new RuntimeException("上报单位不能为空");
        }
        log.debug("上报单位：{}", asset.getReportUnit());
    }

    /**
     * 2.3 分类编码与资产分类校验
     * 规则：必填，与资产分类严格匹配，使用CategoryMapUtils中的网信表映射
     */
    private void validateCategory(CyberAsset asset) {
        if (!StringUtils.hasText(asset.getCategoryCode())) {
            throw new RuntimeException("分类编码不能为空");
        }

        if (!StringUtils.hasText(asset.getAssetCategory())) {
            throw new RuntimeException("资产分类不能为空");
        }

        // 分类匹配校验
        if (!checkCategoryMatch(asset.getCategoryCode(), asset.getAssetCategory())) {
            throw new RuntimeException("分类不匹配！编码" + asset.getCategoryCode() +
                    "对应的正确分类应为：" + CATEGORY_MAP.get(asset.getCategoryCode()));
        }

        log.debug("分类编码：{}，资产分类：{}", asset.getCategoryCode(), asset.getAssetCategory());
    }

    /**
     * 2.4 资产名称校验
     * 规则：必填
     */
    private void validateAssetName(CyberAsset asset) {
        if (!StringUtils.hasText(asset.getAssetName())) {
            throw new RuntimeException("资产名称不能为空");
        }
        log.debug("资产名称：{}", asset.getAssetName());
    }

    /**
     * 2.5 资产内容校验
     * 规则：必填，直接写出资产内容的信息，如IP地址范围段、频谱范围段、手机号码区间段等
     */
    private void validateAssetContent(CyberAsset asset) {
        if (!StringUtils.hasText(asset.getAssetContent())) {
            throw new RuntimeException("资产内容不能为空");
        }

        // 检查资产内容是否包含区间格式（如电话号码区间）
        if (asset.getAssetCategory().contains("电话") || asset.getAssetCategory().contains("号码")) {
            if (!asset.getAssetContent().matches(".*\\[.*,.*\\].*")) {
                log.warn("电话号码类资产内容建议使用区间格式，如[0451-83210000, 0451-83213999]");
            }
        }

        log.debug("资产内容：{}", asset.getAssetContent());
    }

    /**
     * 2.6 实有数量校验
     * 规则：必填，非负整数
     */
    private void validateActualQuantity(CyberAsset asset) {
        if (asset.getActualQuantity() == null) {
            throw new RuntimeException("实有数量不能为空");
        }

        if (asset.getActualQuantity() < 0) {
            throw new RuntimeException("实有数量必须为非负整数");
        }

        log.debug("实有数量：{}", asset.getActualQuantity());
    }

    /**
     * 2.7 计量单位校验
     * 规则：必填，如"个"、"芯"、"条"等，无固定选项
     */
    private void validateUnit(CyberAsset asset) {
        if (!StringUtils.hasText(asset.getUnit())) {
            throw new RuntimeException("计量单位不能为空");
        }
        log.debug("计量单位：{}", asset.getUnit());
    }

    /**
     * 2.8 单价校验
     * 规则：可选，如果填写则必须非负
     */
    private void validateUnitPrice(CyberAsset asset) {
        // 可选字段，如果有值则校验非负
        if (asset.getUnitPrice() != null) {
            if (asset.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("单价不能为负数");
            }
            log.debug("单价：{}", asset.getUnitPrice());
        }
    }

    /**
     * 2.9 投入使用日期校验
     * 规则：必填，日期格式（YYYY-MM-DD）从1949年到现在
     */
    private void validatePutIntoUseDate(CyberAsset asset) {
        if (asset.getPutIntoUseDate() == null) {
            throw new RuntimeException("投入使用日期不能为空");
        }

        LocalDate minDate = LocalDate.of(1949, 10, 1);
        LocalDate maxDate = LocalDate.now();

        if (asset.getPutIntoUseDate().isBefore(minDate)) {
            throw new RuntimeException("投入使用日期不能早于1949年10月1日");
        }

        if (asset.getPutIntoUseDate().isAfter(maxDate)) {
            throw new RuntimeException("投入使用日期不能晚于当前日期");
        }

        log.debug("投入使用日期：{}", asset.getPutIntoUseDate());
    }

    /**
     * 2.10 已用数量校验
     * 规则：必填，非负整数，且≤实有数量
     */
    private void validateUsedQuantity(CyberAsset asset) {
        if (asset.getUsedQuantity() == null) {
            throw new RuntimeException("已用数量不能为空");
        }

        if (asset.getUsedQuantity() < 0) {
            throw new RuntimeException("已用数量必须为非负整数");
        }

        if (asset.getUsedQuantity() > asset.getActualQuantity()) {
            throw new RuntimeException("已用数量不能大于实有数量");
        }

        log.debug("已用数量：{}", asset.getUsedQuantity());
    }

    /**
     * 2.11 盘点单位校验
     * 规则：必填
     */
    private void validateInventoryUnit(CyberAsset asset) {
        if (!StringUtils.hasText(asset.getInventoryUnit())) {
            throw new RuntimeException("盘点单位不能为空");
        }
        log.debug("盘点单位：{}", asset.getInventoryUnit());
    }

// ==================== 金额计算方法 ====================

    /**
     * 金额计算
     * 规则：可选，数量×单价
     * 逻辑：如果金额为空，且有单价和实有数量，则自动计算
     * 如果金额已有值，则不自动计算（尊重用户输入）
     */
    private void calculateAmount(CyberAsset asset) {
        // 只有当金额为空，且单价和实有数量都存在时，才自动计算
        if (asset.getAmount() == null && asset.getUnitPrice() != null && asset.getActualQuantity() != null) {
            BigDecimal amount = asset.getUnitPrice().multiply(BigDecimal.valueOf(asset.getActualQuantity()));
            asset.setAmount(amount);
            log.debug("自动计算金额：单价 {} × 数量 {} = 金额 {}",
                    asset.getUnitPrice(), asset.getActualQuantity(), amount);
        } else if (asset.getAmount() != null) {
            log.debug("使用用户输入的金额：{}", asset.getAmount());
        } else {
            log.debug("金额字段为空，且缺少计算条件（单价或数量）");
        }
    }


// ==================== 1123 核心业务方法 ====================

    /**
     * 🔄 修改网信基础资产 - 完整的业务逻辑实现（优化版）
     *
     * ==================== 方法概述 ====================
     * 本方法处理网信资产的修改操作，是系统中最复杂的业务方法之一。
     * 采用分阶段处理架构，确保数据完整性、业务正确性和状态同步一致性。
     *
     * ==================== 核心特性 ====================
     * ✅ 支持9大修改场景的智能处理
     * ✅ 完整的省市信息推导和标准化处理
     * ✅ 精确的上报单位表状态同步机制
     * ✅ 条件性的跨表数据同步策略
     * ✅ 完整的事务管理和异常处理保障
     *
     * ==================== 9大处理场景 ====================
     * 场景1-3：单独修改省市信息（智能补全）
     *   1. 同时修改省和市 → 标准化处理
     *   2. 只修改省 → 自动补全市（省份首府）
     *   3. 只修改市 → 自动补全省（城市推导）
     *
     * 场景4：只修改上报单位 → 根据新单位推导省市
     *
     * 场景5：无任何修改 → 保持原样
     *
     * 场景6：复合修改（单位+省市同时修改）
     *   6A：正常修改 → 使用用户输入
     *   6B：省市都清空 → 根据单位推导
     *   6C：只清空省 → 省市都设为未知
     *   6D：只清空市 → 市设为未知，省保留
     *   6E：省市部分有效 → 统一有效性处理
     *
     * 场景7：单独清空省市信息
     *   7A：省市都清空 → 根据单位推导
     *   7B：只清空省 → 省市都设为未知
     *   7C：只清空市 → 市设为未知，省保持
     *
     * 场景8：用户输入无效省市
     *   8A：省无效，市有效 → 根据市推导省
     *   8B：省有效，市无效 → 市设为未知
     *   8C：省市都无效 → 都设为未知
     *
     * 场景9：省市信息不一致 → 市设为未知，省保留
     *
     * ==================== 8阶段处理流程 ====================
     * 阶段1：数据存在性校验 → 阶段2：智能省市处理 → 阶段3：业务数据校验
     * 阶段4：金额计算处理 → 阶段5：数据更新操作 → 阶段6：创建时间更新
     * 阶段7：上报单位表同步 → 阶段8：跨表同步决策执行
     *
     * ==================== 事务管理 ====================
     * 使用@Transactional注解确保所有数据库操作的原子性
     * 任何步骤失败都会回滚整个事务，保证数据一致性
     *
     * ==================== 设计原则 ====================
     * 1. 用户输入优先：在正常场景下完全信任用户输入
     * 2. 数据完整性：确保省市字段始终有值，避免空值
     * 3. 智能推导：对缺失信息进行智能补全和推导
     * 4. 状态同步：确保上报单位表与实际数据状态一致
     * 5. 性能优化：避免不必要的跨表同步操作
     *
     * @param asset 网信资产对象（包含用户修改后的数据）
     * @throws RuntimeException 当资产不存在、数据校验失败或更新失败时抛出业务异常
     *
     * @apiNote 本方法是系统中业务逻辑最复杂的方法之一，涉及9种修改场景的处理，
     *          修改时需谨慎测试所有场景，确保逻辑正确性。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(CyberAsset asset) {
        log.info("🔄 [网信资产] 开始修改网信资产，ID：{}", asset.getId());

        // ==================== 阶段1：数据存在性校验 ====================
        log.debug("📋 [阶段1] 开始数据存在性校验");

        // 1.1 主键ID非空校验：确保修改操作有明确的目标记录
        if (!StringUtils.hasText(asset.getId())) {
            throw new RuntimeException("修改网信资产失败：主键ID不能为空");
        }

        // 1.2 原记录查询：获取数据库中现有的资产记录，用于变更比较和数据回滚
        CyberAsset existingAsset = baseMapper.selectById(asset.getId());
        if (existingAsset == null) {
            throw new RuntimeException("修改网信资产失败：资产不存在，ID：" + asset.getId());
        }

        // 1.3 变更信息记录：保存原始数据，用于后续的变更检测和同步决策
        String originalReportUnit = existingAsset.getReportUnit();
        String newReportUnit = asset.getReportUnit();
        String originalProvince = existingAsset.getProvince();
        String originalCity = existingAsset.getCity();
        String newProvince = asset.getProvince();
        String newCity = asset.getCity();

        // 1.4 变更状态分析：精确识别用户修改了哪些字段
        boolean reportUnitChanged = !Objects.equals(originalReportUnit, newReportUnit);
        boolean provinceChanged = !Objects.equals(originalProvince, newProvince);
        boolean cityChanged = !Objects.equals(originalCity, newCity);
        boolean userModifiedProvinceCity = provinceChanged || cityChanged;
        boolean compositeModification = reportUnitChanged && userModifiedProvinceCity;

        log.debug("📋 [阶段1] 数据存在性校验完成 - 单位变更: {}, 省变更: {}, 市变更: {}, 用户修改省市: {}, 复合修改: {}",
                reportUnitChanged, provinceChanged, cityChanged, userModifiedProvinceCity, compositeModification);

        // ==================== 阶段2：智能省市处理 ====================
        log.debug("🌍 [阶段2] 开始智能省市处理");

        if (compositeModification) {
            // 🎯 场景6：复合修改（单位+省市同时修改）
            log.info("🎯 检测到复合修改场景：同时修改单位和省市");
            handleCompositeModification(asset, existingAsset);
        } else if (userModifiedProvinceCity) {
            // 🎯 场景7+8+9+1-3：单独修改省市信息
            log.debug("🎯 用户手动修改了省市信息");

            // 第一步：处理清空场景（场景7）
            handleClearedProvinceCity(asset, existingAsset);

            // 第二步：统一处理有效性（整合场景6E + 场景8 + 场景9）
            handleProvinceCityValidity(asset);

            // 第三步：进行智能补全（场景1-3）- 只有在清空和有效性处理后仍有空值时
            if (!StringUtils.hasText(asset.getProvince()) || !StringUtils.hasText(asset.getCity()) ||
                    "未知".equals(asset.getProvince()) || "未知".equals(asset.getCity())) {
                log.debug("🎯 省市仍有空值或未知，进行智能补全");
                handleUserModifiedProvinceCity(asset, existingAsset);
            }
        } else if (reportUnitChanged) {
            // 🎯 场景4：只修改单位
            log.debug("🎯 用户修改了上报单位，重新推导省市");
            handleUnitChangedProvinceCity(asset, newReportUnit);
        } else {
            // 🎯 场景5：无修改
            log.debug("🎯 用户未修改省市和单位，保持原有省市");
        }

        log.debug("🌍 [阶段2] 智能省市处理完成 - 最终省市: {}-{}", asset.getProvince(), asset.getCity());


// ==================== 阶段3：省市字段严格校验 ====================
//        log.debug("🔍 [阶段3] 开始省市字段校验");
//        validateProvinceCity(asset.getProvince(), asset.getCity());

        // ==================== 阶段4：业务数据校验 ====================
        log.debug("✅ [阶段3] 开始业务数据校验");
        validateBusinessFields(asset);

        log.debug("✅ [阶段3] 业务数据校验通过，ID：{}", asset.getId());

        // ==================== 阶段4：金额计算处理 ====================
        log.debug("💰 [阶段4] 金额计算处理");
        calculateAmount(asset);

        // ==================== 阶段5+阶段6：数据更新+创建时间更新 ====================
        log.debug("💾 [阶段5] 开始数据更新");

        // 5.1 更新创建时间为当前时间（作为最后修改时间的参考）
        asset.setCreateTime(LocalDateTime.now());

        // 6.1 执行数据库更新操作
        int updateCount = baseMapper.updateById(asset);
        if (updateCount == 0) {
            throw new RuntimeException("修改网信资产失败，ID：" + asset.getId());
        }

        log.info("✅ [阶段5-6] 修改网信资产成功，ID：{}，资产名称：{}", asset.getId(), asset.getAssetName());

        // ==================== 阶段7：上报单位表同步 ====================
        log.debug("🔄 [阶段7] 开始上报单位表同步");

        /**
         * 📍 上报单位表同步触发条件（优化版）：
         * 1. 修改了上报单位 → 必须同步（更新原单位状态 + 新增/更新新单位）
         * 2. 修改了省市 → 必须同步（更新单位对应的省市信息）
         * 3. 系统自动修正了省市 → 必须同步（确保数据一致性）
         *
         * 🆕 新增：检测最终省市是否与原始省市不同，覆盖系统自动修正的情况
         */
        boolean provinceCityActuallyChanged = !Objects.equals(existingAsset.getProvince(), asset.getProvince()) ||
                !Objects.equals(existingAsset.getCity(), asset.getCity());
        // // 1.用户修改了单位 2.用户修改了省市 3.🆕 系统自动修正了
        boolean needUnitSync = reportUnitChanged || userModifiedProvinceCity || provinceCityActuallyChanged;

        if (needUnitSync) {
            log.debug("🔄 触发上报单位表同步 - 单位变更: {}, 用户修改省市: {}, 实际省市变化: {}",
                    reportUnitChanged, userModifiedProvinceCity, provinceCityActuallyChanged);

            syncReportUnitWithChange(originalReportUnit, newReportUnit,
                    existingAsset.getProvince(), asset.getProvince(),
                    reportUnitChanged, userModifiedProvinceCity || provinceCityActuallyChanged);
        } else {
            log.debug("⏭️ 未触发上报单位表同步 - 单位、省市和实际省市均未变化");
        }

        // ==================== 场景10：级联更新同一单位的其他记录 ====================
        log.debug("🔄 [场景10] 开始检查级联更新");
        if (reportUnitChanged || provinceCityActuallyChanged) {
            log.info("🎯 场景10：检测到单位或省市变更，开始级联更新");

            // 确定要更新的单位：如果单位变更则用新单位，否则用原单位
            String unitToUpdate = reportUnitChanged ? newReportUnit : originalReportUnit;

            handleCascadeUpdateForUnit(unitToUpdate, asset.getProvince(), asset.getCity(), asset.getId());
        } else {
            log.debug("⏭️ 场景10跳过：单位和省市均未变更");
        }

        // ==================== 阶段8：跨表同步决策与执行 ====================
        log.debug("🔄 [阶段8] 开始跨表同步决策");

        /**
         * 📍 跨表同步触发条件（优化版）：
         * 1. 省市必须发生改变（比较最终省市和原始省市）
         * 2. 单位必须在上报单位表中存在
         * 3. 新单位名称不能为空
         *
         * 🆕 优化：直接使用最终省市进行比较，确保系统自动修正也能触发同步
         */
        boolean needCrossSync = needCrossTableSync(newReportUnit,
                existingAsset.getProvince(), existingAsset.getCity(),  // 使用原始省市
                asset.getProvince(), asset.getCity());                 // 使用最终省市

        if (needCrossSync) {
            log.info("🔄 满足跨表同步条件，开始跨表同步");
            syncToDataTable(newReportUnit, asset.getProvince(), asset.getCity());
            log.info("✅ 跨表同步完成");
        } else {
            log.debug("⏭️ 不满足跨表同步条件，跳过同步");
        }

        // ==================== 修改操作结束 ====================
        log.info("🎉 [网信资产] 修改操作全部完成，ID：{}", asset.getId());
    }



    /**
     * 🎯 场景6：复合修改（单位+省市同时修改）
     *
     * ==================== 方法说明 ====================
     * 处理用户同时修改单位和省市信息的复杂场景，这是最复杂的修改情况。
     * 根据用户的具体输入情况，采用不同的处理策略。
     *
     * ==================== 细分场景 ====================
     * 6A：单位修改 + 省市都修改（正常情况）→ 使用用户输入
     * 6B：单位修改 + 省市都清空/未知/空白 → 根据新单位推导
     * 6C：单位修改 + 只清空省/省为空白 → 省设为未知，市也设为未知
     * 6D：单位修改 + 只清空市/市为空白 → 市设为未知，省保留（如果有效）
     * 6E：单位修改 + 修改后的省市部分有效 →
     *     - 若省有效，市无效 → 省保留，市设为未知
     *     - 若市有效，省无效 → 根据市推导省，市保留
     *     - 若都无效 → 都设置为未知
     *
     * ==================== 设计原则 ====================
     * 1. 用户输入优先：在正常修改场景下完全信任用户输入
     * 2. 完整性保证：确保省市字段始终有值，避免空值
     * 3. 同步一致性：确保修改后的数据能够正确同步到上报单位表
     *
     * @param asset 当前资产对象（包含用户修改后的数据）
     * @param existingAsset 原始资产对象（用于获取原始信息）
     */
    private void handleCompositeModification(CyberAsset asset, CyberAsset existingAsset) {
        String userProvince = asset.getProvince();
        String userCity = asset.getCity();
        String newReportUnit = asset.getReportUnit();

        log.info("🤖 复合修改场景处理 - 用户输入: 单位={}, 省={}, 市={}",
                newReportUnit, userProvince, userCity);

        // 🎯 场景6B：省市都清空/未知/空白 → 根据新单位推导
        if ((!StringUtils.hasText(userProvince) || "未知".equals(userProvince)) &&
                (!StringUtils.hasText(userCity) || "未知".equals(userCity))) {
            log.info("🎯 场景6B：单位修改 + 省市都清空/未知/空白，根据新单位推导");
            useToolToDeriveProvinceCity(asset, newReportUnit);
            return;
        }

        // 🎯 场景6C：只清空省/省为空白 → 省设为未知，市也设为未知
        if ((!StringUtils.hasText(userProvince) || "未知".equals(userProvince)) &&
                StringUtils.hasText(userCity) && !"未知".equals(userCity)) {
            log.info("🎯 场景6C：单位修改 + 只清空省/省为空白，省市都设为未知");
            asset.setProvince("未知");
            asset.setCity("未知");
            return;
        }

        // 🎯 场景6D：只清空市/市为空白 → 市设为未知，省保留（如果有效）
        if (StringUtils.hasText(userProvince) && !"未知".equals(userProvince) &&
                (!StringUtils.hasText(userCity) || "未知".equals(userCity))) {
            log.info("🎯 场景6D：单位修改 + 只清空市/市为空白，市设为未知，省保留");
            asset.setCity("未知");
            // 检查省是否有效，无效则设为未知
            if (!isProvinceValid(userProvince)) {
                asset.setProvince("未知");
                log.warn("⚠️ 清空市时发现省无效，省也设为未知");
            }
            return;
        }

        // 🎯 场景6E：省市部分有效 → 使用统一的有效性处理
        log.info("🎯 场景6E：单位修改 + 省市部分有效，进行统一有效性处理");
        handleProvinceCityValidity(asset);

        log.info("✅ 复合修改处理完成 - 最终: 单位={}, 省={}, 市={}",
                asset.getReportUnit(), asset.getProvince(), asset.getCity());
    }

    /**
     * 🎯 场景7：用户清空省市信息（单位未修改）
     *
     * ==================== 方法说明 ====================
     * 处理用户单独清空省市字段的情况，确保数据完整性和同步正确性。
     * 根据清空的具体字段，采用不同的处理策略。
     *
     * ==================== 细分场景 ====================
     * 7A：省市都清空/未知/空白 → 根据单位推导
     * 7B：只清空省/省为空白 → 省市都改为未知
     * 7C：只清空市/市为空白 → 市改为未知，省保持
     *
     * ==================== 设计原则 ====================
     * 1. 完整性优先：确保省市字段始终有值
     * 2. 同步保证：清空操作必须确保能够正确同步到上报单位表
     * 3. 用户意图：尊重用户清空操作，但提供合理的默认值
     *
     * @param asset 当前资产对象（包含用户修改后的数据）
     * @param existingAsset 原始资产对象（用于获取原始信息）
     */
    private void handleClearedProvinceCity(CyberAsset asset, CyberAsset existingAsset) {
        String userProvince = asset.getProvince();
        String userCity = asset.getCity();
        String originalProvince = existingAsset.getProvince();
        String originalCity = existingAsset.getCity();

        // 检测用户是否清空了省市（从有值变为空/未知）
        boolean provinceCleared = (StringUtils.hasText(originalProvince) && !"未知".equals(originalProvince)) &&
                (!StringUtils.hasText(userProvince) || "未知".equals(userProvince));
        boolean cityCleared = (StringUtils.hasText(originalCity) && !"未知".equals(originalCity)) &&
                (!StringUtils.hasText(userCity) || "未知".equals(userCity));

        if (!provinceCleared && !cityCleared) {
            return; // 没有清空操作
        }

        log.info("🎯 场景7：检测到用户清空省市信息 - 省清空: {}, 市清空: {}", provinceCleared, cityCleared);

        // 🎯 场景7A：省市都清空/未知/空白 → 根据单位推导
        if (provinceCleared && cityCleared) {
            log.info("🎯 场景7A：省市都清空/未知/空白，根据单位推导");
            String unitToUse = StringUtils.hasText(asset.getReportUnit()) ?
                    asset.getReportUnit() : existingAsset.getReportUnit();
            if (StringUtils.hasText(unitToUse)) {
                useToolToDeriveProvinceCity(asset, unitToUse);
            } else {
                asset.setProvince("未知");
                asset.setCity("未知");
                log.warn("⚠️ 无法推导省市：单位和省市均为空");
            }
            return;
        }

        // 🎯 场景7B：只清空省/省为空白 → 省市都改为未知
        if (provinceCleared) {
            log.info("🎯 场景7B：只清空省/省为空白，省市都改为未知");
            asset.setProvince("未知");
            asset.setCity("未知");
            return;
        }

        // 🎯 场景7C：只清空市/市为空白 → 市改为未知，省保持
        if (cityCleared) {
            log.info("🎯 场景7C：只清空市/市为空白，市改为未知，省保持");
            asset.setCity("未知");
            // 省保持不变
        }
    }

    /**
     * 🎯 统一的省市有效性处理（整合场景6E + 场景8 + 场景9）
     *
     * ==================== 方法说明 ====================
     * 统一处理省市字段的有效性、一致性和完整性。
     * 这是所有修改场景的最终保障，确保保存到数据库的数据是完整有效的。
     *
     * ==================== 处理逻辑 ====================
     * 1. 省市都有效但不一致 → 市设为未知，省保留（场景9）
     * 2. 省有效，市无效 → 省保留，市设为未知（场景8B）
     * 3. 省无效，市有效 → 根据市推导省，市保留（场景8A）
     * 4. 省市都无效 → 省市都设为未知（场景8C）
     *
     * ==================== 设计原则 ====================
     * 1. 用户输入优先：在可能的情况下保留用户输入
     * 2. 数据完整性：确保省市字段始终有值
     * 3. 逻辑一致性：确保省市关系在逻辑上正确
     * 4. 同步正确性：确保修改后的数据能够正确同步
     *
     * @param asset 当前资产对象
     */
    private void handleProvinceCityValidity(CyberAsset asset) {
        String userProvince = asset.getProvince();
        String userCity = asset.getCity();

        // 检查有效性
        boolean provinceValid = isProvinceValid(userProvince);
        boolean cityValid = isCityValid(userCity);

        log.debug("🔍 省市有效性检查 - 省: {} (有效: {}), 市: {} (有效: {})",
                userProvince, provinceValid, userCity, cityValid);

        // 1. 省市都有效但不一致 → 市设为未知，省保留（场景9）
        if (provinceValid && cityValid) {
            if (!isProvinceCityConsistent(userProvince, userCity)) {
                log.warn("🎯 场景9：省市不一致，市设为未知 - 省: {}, 市: {}", userProvince, userCity);
                asset.setCity("未知");
                return;
            }
            // 都有效且一致，无需处理
            log.debug("✅ 省市都有效且一致，无需处理");
            return;
        }

        // 2. 省有效，市无效 → 省保留，市设为未知（场景8B）
        if (provinceValid && !cityValid) {
            log.warn("🎯 场景8B：市无效，市设为未知 - 省: {}, 市: {}", userProvince, userCity);
            asset.setCity("未知");
            return;
        }

        // 3. 省无效，市有效 → 根据市推导省，市保留（场景8A）
        if (!provinceValid && cityValid) {
            log.warn("🎯 场景8A：省无效，根据市推导省 - 原省: {}, 市: {}", userProvince, userCity);
            String derivedProvince = deriveProvinceFromCity(userCity);
            if (derivedProvince != null && !"未知".equals(derivedProvince)) {
                asset.setProvince(derivedProvince);
                log.info("✅ 根据市推导省成功 - 新省: {}, 市: {}", derivedProvince, userCity);
            } else {
                asset.setProvince("未知");
                log.warn("⚠️ 无法根据市推导省，省设为未知");
            }
            return;
        }

        // 4. 省市都无效 → 省市都设为未知（场景8C）
        if (!provinceValid && !cityValid) {
            log.warn("🎯 场景8C：省市都无效，都设为未知 - 省: {}, 市: {}", userProvince, userCity);
            asset.setProvince("未知");
            asset.setCity("未知");
        }
    }

    /**
     * 🎯 场景10：级联更新同一单位的其他记录
     *
     * 当修改某条记录的单位或省市信息时，同步更新本表中所有使用相同单位的其他记录，
     * 确保同一单位在所有记录中的省市信息保持一致。
     *
     * @param unitName 要更新的单位名称
     * @param newProvince 新的省份
     * @param newCity 新的城市
     * @param excludeId 要排除的记录ID（当前修改的记录）
     */
    private void handleCascadeUpdateForUnit(String unitName, String newProvince, String newCity, String excludeId) {
        if (!StringUtils.hasText(unitName)) {
            log.debug("⏭️ 场景10跳过：单位名称为空");
            return;
        }

        try {
            // 1. 查询相同单位的其他记录（排除当前记录）
            List<CyberAsset> sameUnitAssets = baseMapper.selectByReportUnitExcludeId(unitName, excludeId);

            if (sameUnitAssets == null || sameUnitAssets.isEmpty()) {
                log.debug("⏭️ 场景10跳过：单位[{}]没有其他记录需要更新", unitName);
                return;
            }

            log.info("🎯 场景10：开始更新单位[{}]的{}条记录", unitName, sameUnitAssets.size());

            // 2. 批量更新这些记录
            int updatedCount = 0;
            for (CyberAsset asset : sameUnitAssets) {
                // 记录原始值用于日志
                String oldProvince = asset.getProvince();
                String oldCity = asset.getCity();

                // 检查是否需要更新（避免不必要的更新）
                if (!Objects.equals(oldProvince, newProvince) || !Objects.equals(oldCity, newCity)) {
                    // 更新省市字段和创建时间
                    asset.setProvince(newProvince);
                    asset.setCity(newCity);
                    asset.setCreateTime(LocalDateTime.now()); // ✅ 按照您的要求：更新createTime

                    int count = baseMapper.updateById(asset);
                    if (count > 0) {
                        updatedCount++;
                        log.info("✅ 场景10：更新记录[{}] {}-{} → {}-{}",
                                asset.getId(), oldProvince, oldCity, newProvince, newCity);
                    }
                }
            }

            log.info("✅ 场景10：完成级联更新，成功更新{}/{}条记录", updatedCount, sameUnitAssets.size());

        } catch (Exception e) {
            log.error("❌ 场景10：级联更新失败，单位[{}]", unitName, e);
        }
    }

    // ==================== 新增辅助检查方法 (有效性+一致性) ====================
    /**
     * 🎯 检查省份有效性
     *
     * ==================== 有效性标准 ====================
     * 1. 非空且不是"未知"
     * 2. 在AreaCacheTool的省份列表中存在
     * 3. 标准化后能够匹配到标准省份名称
     *
     * @param province 省份名称
     * @return 是否有效
     */
    private boolean isProvinceValid(String province) {
        if (!StringUtils.hasText(province) || "未知".equals(province)) {
            return false; // 空值和"未知"认为是无效的（需要处理）
        }

        // 标准化后检查是否在有效列表中
        String standardized = standardizeProvinceName(province);
        boolean valid = areaCacheTool.getAllProvinceNames().contains(standardized);

        log.debug("🔍 省份有效性检查: '{}' -> '{}' -> {}", province, standardized, valid);
        return valid;
    }

    /**
     * 🎯 检查城市有效性
     *
     * ==================== 有效性标准 ====================
     * 1. 非空且不是"未知"
     * 2. 在AreaCacheTool的城市列表中存在
     * 3. 标准化后能够匹配到标准城市名称
     *
     * @param city 城市名称
     * @return 是否有效
     */
    private boolean isCityValid(String city) {
        if (!StringUtils.hasText(city) || "未知".equals(city)) {
            return false; // 空值和"未知"认为是无效的（需要处理）
        }

        // 标准化后检查是否在有效列表中
        String standardized = standardizeCityName(city);
        boolean valid = areaCacheTool.getAllCityNames().contains(standardized);

        log.debug("🔍 城市有效性检查: '{}' -> '{}' -> {}", city, standardized, valid);
        return valid;
    }

    /**
     * 🎯 检查省市是否一致
     *
     * ==================== 一致性标准 ====================
     * 1. 城市对应的省份与用户输入的省份一致
     * 2. 使用AreaCacheTool的城市到省份映射进行验证
     * 3. 空值或"未知"认为是一致的（避免过度处理）
     *
     * @param province 省份名称
     * @param city 城市名称
     * @return 是否一致
     */
    private boolean isProvinceCityConsistent(String province, String city) {
        if (!StringUtils.hasText(province) || !StringUtils.hasText(city) ||
                "未知".equals(province) || "未知".equals(city)) {
            return true; // 空值或未知认为一致（避免过度处理）
        }

        Map<String, String> cityToProvinceMap = areaCacheTool.getCityToProvinceMap();
        String actualProvince = cityToProvinceMap.get(city);
        boolean consistent = actualProvince != null && actualProvince.equals(province);

        log.debug("🔍 省市一致性检查: {}-{} -> 实际: {}-{} -> {}",
                province, city, actualProvince, city, consistent);
        return consistent;
    }

    /**
     * 🎯 根据城市推导省份
     *
     * ==================== 推导逻辑 ====================
     * 1. 使用AreaCacheTool的城市到省份映射
     * 2. 如果映射中存在，返回对应的省份
     * 3. 如果映射中不存在，返回"未知"
     *
     * @param city 城市名称
     * @return 推导出的省份名称，如无法推导返回"未知"
     */
    private String deriveProvinceFromCity(String city) {
        if (!StringUtils.hasText(city) || "未知".equals(city)) {
            return "未知";
        }

        Map<String, String> cityToProvinceMap = areaCacheTool.getCityToProvinceMap();
        String province = cityToProvinceMap.get(city);

        log.debug("🔍 根据城市推导省份: '{}' -> '{}'", city, province);
        return province != null ? province : "未知";
    }

    /**
     * 🎯 处理用户手动修改省市的情况（场景1-3）- 优化版
     *
     * ==================== 方法说明 ====================
     * 在清空和有效性处理之后，对仍有空值的省市进行智能补全。
     * 这是正常的省市修改处理逻辑，确保省市信息的完整性。
     *
     * ==================== 场景覆盖 ====================
     * 场景1：用户同时修改了省和市 → 直接标准化处理
     * 场景2：用户只修改了省 → 补全市信息（省份首府）
     * 场景3：用户只修改了市 → 补全省信息（根据城市推导省份）
     *
     * ==================== 调用时机 ====================
     * 只有在清空处理（场景7）和有效性处理（场景6E+8+9）之后，
     * 省市字段仍有空值或"未知"时才调用此方法。
     *
     * @param asset 当前资产对象（包含用户修改后的数据）
     * @param existingAsset 原始资产对象（用于比较哪些字段被修改）
     */
    private void handleUserModifiedProvinceCity(CyberAsset asset, CyberAsset existingAsset) {
        String userProvince = asset.getProvince();
        String userCity = asset.getCity();
        String originalProvince = existingAsset.getProvince();
        String originalCity = existingAsset.getCity();

        boolean provinceChanged = !Objects.equals(originalProvince, userProvince);
        boolean cityChanged = !Objects.equals(originalCity, userCity);

        log.debug("🤖 用户修改省市分析 - 省变更: {}, 市变更: {}, 用户输入: {}-{}",
                provinceChanged, cityChanged, userProvince, userCity);

        if (provinceChanged && cityChanged) {
            // 🎯 场景1：用户同时修改了省和市
            log.debug("🎯 场景1：用户同时修改了省和市，进行标准化处理");
            standardizeProvinceCity(asset);

        } else if (provinceChanged && !cityChanged) {
            // 🎯 场景2：用户只修改了省，未修改市
            log.debug("🎯 场景2：用户只修改了省，补全市信息（省份首府）");

            // 先标准化省份名称
            String standardizedProvince = standardizeProvinceName(userProvince);
            asset.setProvince(standardizedProvince);

            try {
                String capital = areaCacheTool.getCapitalByProvinceName(standardizedProvince);
                if (StringUtils.hasText(capital)) {
                    asset.setCity(capital);
                    log.debug("✅ 成功补全首府 - 省: {}, 市: {}", standardizedProvince, capital);
                } else {
                    log.warn("⚠️ 无法找到省份的首府，市设为未知");
                    asset.setCity("未知");
                }
            } catch (Exception e) {
                log.error("❌ 获取首府时出错，市设为未知", e);
                asset.setCity("未知");
            }

        } else if (!provinceChanged && cityChanged) {
            // 🎯 场景3：用户只修改了市，未修改省
            log.debug("🎯 场景3：用户只修改了市，补全省信息");

            // 先标准化城市名称
            String standardizedCity = standardizeCityName(userCity);
            asset.setCity(standardizedCity);

            try {
                // 使用增强的城市到省份映射
                String province = findProvinceByCity(standardizedCity);

                if (StringUtils.hasText(province)) {
                    asset.setProvince(province);
                    log.debug("✅ 成功推导省份 - 市: {}, 省: {}", standardizedCity, province);
                } else {
                    log.warn("⚠️ 无法根据城市推导省份，省设为未知");
                    asset.setProvince("未知");
                }
            } catch (Exception e) {
                log.error("❌ 获取省份时出错，省设为未知", e);
                asset.setProvince("未知");
            }
        }
    }

    /**
     * 🎯 处理单位变更时的省市推导（场景4）- 优化版
     *
     * ==================== 方法说明 ====================
     * 当用户只修改上报单位时，智能推导新单位对应的省市信息。
     * 优先从上报单位表获取信息，不存在时使用工具类推导。
     *
     * ==================== 优化策略 ====================
     * 策略1：查询上报单位表，如果单位存在且省份有效 → 直接使用该省份，补全首府
     * 策略2：如果单位不存在或省份无效 → 使用工具类智能推导
     *
     * @param asset 当前资产对象
     * @param newReportUnit 新的上报单位名称
     */
    private void handleUnitChangedProvinceCity(CyberAsset asset, String newReportUnit) {
        log.debug("🤖 单位变更，开始推导省市 - 新单位: {}", newReportUnit);

        // 🎯 策略1：优先从上报单位表中获取省份信息
        ReportUnit reportUnit = reportUnitMapper.selectByReportUnitName(newReportUnit);
        if (reportUnit != null && StringUtils.hasText(reportUnit.getProvince()) &&
                !"未知".equals(reportUnit.getProvince())) {

            // 🎯 单位表中存在有效省份，直接使用并补全首府
            String provinceFromTable = reportUnit.getProvince();
            asset.setProvince(provinceFromTable);

            try {
                String capital = areaCacheTool.getCapitalByProvinceName(provinceFromTable);
                if (StringUtils.hasText(capital)) {
                    asset.setCity(capital);
                    log.info("✅ 从上报单位表获取省市成功 - 单位: {}, 省: {}, 市: {}",
                            newReportUnit, provinceFromTable, capital);
                } else {
                    log.warn("⚠️ 无法找到省份的首府，使用工具类推导城市");
                    useToolToDeriveCity(asset, provinceFromTable);
                }
            } catch (Exception e) {
                log.error("❌ 获取首府时出错，使用工具类推导", e);
                useToolToDeriveCity(asset, provinceFromTable);
            }
        } else {
            // 🎯 策略2：单位表中不存在，使用工具类完整推导
            log.debug("🔍 上报单位表中无记录，使用工具类推导");
            useToolToDeriveProvinceCity(asset, newReportUnit);
        }
    }

    // ==================== 🆕 新增：增强的省市匹配方法 ====================

    /**
     * 🆕 根据城市名称查找对应的省份（增强版）

     * ==================== 方法说明 ====================
     * 使用标准化的城市名称和简写匹配逻辑，提高城市到省份映射的准确性。
     * 支持多种行政区划类型：地级市、县级市、自治州、地区、盟、特别行政区等。

     * ==================== 匹配策略 ====================
     * 1. 精确匹配：直接在城市到省份映射表中查找
     * 2. 简写匹配：使用城市简写进行匹配
     * 3. 标准化匹配：对输入城市名称进行标准化后再匹配

     * @param cityName 城市名称（支持全称或简写）
     * @return 对应的省份名称，如未找到返回null
     */
    private String findProvinceByCity(String cityName) {
        if (!StringUtils.hasText(cityName)) {
            return null;
        }

        Map<String, String> cityToProvinceMap = areaCacheTool.getCityToProvinceMap();

        // 1. 精确匹配：直接查找
        if (cityToProvinceMap.containsKey(cityName)) {
            return cityToProvinceMap.get(cityName);
        }

        // 2. 简写匹配：遍历所有城市，使用简写进行匹配
        for (String standardCity : areaCacheTool.getAllCityNames()) {
            String cityAbbr = getCityAbbreviation(standardCity);
            if (cityName.equals(cityAbbr)) {
                log.debug("🔍 城市简写匹配成功: '{}' → '{}' → '{}'",
                        cityName, cityAbbr, standardCity);
                return cityToProvinceMap.get(standardCity);
            }
        }

        // 3. 标准化匹配：对输入进行标准化后再尝试
        String standardizedCity = standardizeCityName(cityName);
        if (!cityName.equals(standardizedCity) && cityToProvinceMap.containsKey(standardizedCity)) {
            log.debug("🔍 城市标准化匹配成功: '{}' → '{}'", cityName, standardizedCity);
            return cityToProvinceMap.get(standardizedCity);
        }

        log.debug("❌ 未找到城市对应的省份: {}", cityName);
        return null;
    }

    // ==================== 工具类调用方法 ====================

    /**
     * 🛠️ 使用工具类推导城市（已知省份）

     * ==================== 方法说明 ====================
     * 在已知省份的情况下，使用工具类推导对应的城市信息。
     * 通过创建临时对象适配工具类接口，实现精确的城市推导。

     * ==================== 技术实现 ====================
     * 使用匿名内部类实现HasReportUnitAndProvince接口
     * 固定省份信息，只推导城市信息
     * 调用ProvinceAutoFillTool的非更新模式进行推导

     * @param asset 当前资产对象
     * @param province 已知的省份名称
     *
     * @apiNote 此方法适用于已知省份但需要推导城市的情况，确保推导逻辑的统一性
     */
    private void useToolToDeriveCity(CyberAsset asset, String province) {
        // 创建临时对象，实现 HasReportUnitAndProvince 接口
        HasReportUnitAndProvince tempAsset = new HasReportUnitAndProvince() {
            @Override
            public String getReportUnit() {
                return asset.getReportUnit();
            }

            @Override
            public String getProvince() {
                return province; // 固定省份，不进行修改
            }

            @Override
            public void setProvince(String p) {
                // 不修改省份，因为我们已经固定了省份
            }

            @Override
            public String getCity() {
                return asset.getCity();
            }

            @Override
            public void setCity(String city) {
                asset.setCity(city);
            }
        };

        // 使用工具类推导城市（非更新模式）
        provinceAutoFillTool.fillAssetProvinceCity(tempAsset, false);
        log.debug("🤖 工具类推导城市完成 - 省: {}, 市: {}", province, asset.getCity());
    }

    /**
     * 🛠️ 使用工具类完整推导省市

     * ==================== 方法说明 ====================
     * 当单位在上报单位表中不存在时，使用工具类进行完整的省市推导。
     * 工具类会根据单位名称智能推导出最合适的省市信息。

     * ==================== 推导逻辑 ====================
     * 工具类内部实现复杂的推导逻辑：
     * 县级信息 → 城市信息 → 省份信息 → 战区信息 → 默认"未知"

     * ==================== 技术实现 ====================
     * 创建临时适配器对象，传递完整的资产信息
     * 调用工具类的完整推导功能
     * 使用非更新模式，确保推导逻辑的完整性
     *
     * @param asset 当前资产对象
     * @param reportUnit 上报单位名称
     *
     * @apiNote 此方法委托给专业的工具类处理，确保推导逻辑的统一性和准确性
     */
    private void useToolToDeriveProvinceCity(CyberAsset asset, String reportUnit) {
        // 创建临时对象，实现 HasReportUnitAndProvince 接口
        HasReportUnitAndProvince tempAsset = new HasReportUnitAndProvince() {
            @Override
            public String getReportUnit() {
                return reportUnit;
            }

            @Override
            public String getProvince() {
                return asset.getProvince();
            }

            @Override
            public void setProvince(String province) {
                asset.setProvince(province);
            }

            @Override
            public String getCity() {
                return asset.getCity();
            }

            @Override
            public void setCity(String city) {
                asset.setCity(city);
            }
        };

        // 使用工具类完整推导省市（非更新模式）
        provinceAutoFillTool.fillAssetProvinceCity(tempAsset, false);
        log.debug("🤖 工具类完整推导完成 - 单位: {}, 省市: {}-{}",
                reportUnit, asset.getProvince(), asset.getCity());
    }

    // ==================== 同步相关方法 ====================

    /**
     * 🔄 精确的上报单位表同步方法（优化版）
     *
     * ==================== 同步策略（优化） ====================
     * 情况1：单位变更（无论省市是否变更）
     *   - 原单位：标记删除检查
     *   - 新单位：新增或更新（使用最终省市信息）
     *
     * 情况2：仅省市变更（单位不变）
     *   - 当前单位：更新省市信息（使用最终省市信息）
     *
     * 🆕 优化：确保使用最终省市信息进行同步，覆盖系统自动修正的情况
     * 🎯 资产类型：CyberAsset（网信基础资产）
     */
    private void syncReportUnitWithChange(String originalUnit, String newUnit,
                                          String originalProvince, String newProvince,
                                          boolean unitChanged, boolean provinceChanged) {
        log.debug("🔄 开始精确上报单位表同步 - 单位变更: {}, 省市变更: {}", unitChanged, provinceChanged);

        if (unitChanged) {
            // 🎯 情况1：单位变更，需要处理原单位和新单位

            // 1. 原单位：标记删除检查（使用原始省市）
            if (StringUtils.hasText(originalUnit)) {
                provinceAutoFillTool.syncReportUnit(originalUnit, originalProvince, "cyber", true);
                log.debug("✅ 原单位标记删除检查完成: {}", originalUnit);
            }

            // 2. 新单位：新增或更新（使用最终省市）
            if (StringUtils.hasText(newUnit)) {
                provinceAutoFillTool.syncReportUnit(newUnit, newProvince, "cyber", false);
                log.debug("✅ 新单位同步完成: {} -> {}-{}", newUnit, newProvince, "待推导");
            }

        } else if (provinceChanged) {
            // 🎯 情况2：只修改省市，更新当前单位（使用最终省市）
            if (StringUtils.hasText(newUnit)) {
                provinceAutoFillTool.syncReportUnit(newUnit, newProvince, "cyber", false);
                log.debug("✅ 单位省市更新完成: {} -> {}", newUnit, newProvince);
            }
        }

        log.info("✅ 上报单位表同步完成");
    }

    /**
     * 🔍 跨表同步条件判断（修正版）
     *
     * ==================== 触发条件 ====================
     * 条件1：省市必须发生改变（比较最终省市和原始省市）
     * 条件2：单位在数据内容资产表中必须存在
     * 条件3：新单位名称不能为空
     *
     * @param newUnit 新单位名称
     * @param oldProvince 原始省份
     * @param oldCity 原始城市
     * @param newProvince 最终省份（可能经过系统修正）
     * @param newCity 最终城市（可能经过系统修正）
     * @return 是否需要跨表同步
     */
    private boolean needCrossTableSync(String newUnit, String oldProvince, String oldCity,
                                       String newProvince, String newCity) {
        // 条件1：省市必须发生改变（使用最终省市进行比较）
        boolean provinceCityChanged = !Objects.equals(oldProvince, newProvince) ||
                !Objects.equals(oldCity, newCity);

        if (!provinceCityChanged) {
            log.debug("⏭️ 跨表同步跳过：省市未发生变化 {}-{} → {}-{}",
                    oldProvince, oldCity, newProvince, newCity);
            return false;
        }

        // 条件2：单位名称不能为空
        if (!StringUtils.hasText(newUnit)) {
            log.debug("⏭️ 跨表同步跳过：单位名称为空");
            return false;
        }

        // 🎯 修正：检查单位在数据内容资产表中是否存在
        boolean unitExists = checkUnitExistsInDataContentTable(newUnit);
        if (!unitExists) {
            log.debug("⏭️ 跨表同步跳过：单位在数据内容资产表中不存在 - {}", newUnit);
            return false;
        }

        log.info("✅ 满足跨表同步条件 - 单位: {}, 省市变化: {}-{} → {}-{}",
                newUnit, oldProvince, oldCity, newProvince, newCity);
        return true;
    }

    /**
     * 🔄 跨表同步到数据资产表

     * ==================== 方法说明 ====================
     * 将网信资产的省市变更同步到数据资产表中相同单位的记录。
     * 只同步省市字段，其他字段保持不变，确保数据一致性。

     * ==================== 同步逻辑 ====================
     * 1. 创建更新实体，设置新的省市信息
     * 2. 构建查询条件，匹配相同上报单位的记录
     * 3. 执行批量更新操作
     * 4. 记录详细的同步日志

     * ==================== 技术实现 ====================
     * 使用MyBatis-Plus的QueryWrapper构建查询条件
     * 调用DataContentAssetMapper的update方法进行批量更新
     * 完整的异常处理和日志记录
     *
     * @param reportUnit 上报单位名称
     * @param province 新的省份
     * @param city 新的城市
     *
     * @apiNote 跨表同步是单向的：网信表 → 数据表
     *          数据表修改时也会有相应的同步逻辑到网信表
     */
    private void syncToDataTable(String reportUnit, String province, String city) {
        try {
            // 创建更新实体
            DataContentAsset updateEntity = new DataContentAsset();
            updateEntity.setProvince(province);
            updateEntity.setCity(city);

            // 构建查询条件
            QueryWrapper<DataContentAsset> wrapper = new QueryWrapper<>();
            wrapper.eq("report_unit", reportUnit);

            // 执行批量更新
            int updateCount = dataContentAssetMapper.update(updateEntity, wrapper);
            log.info("✅ 跨表同步完成 - 数据表单位: {}, 更新记录数: {}, 新省市: {}-{}",
                    reportUnit, updateCount, province, city);
        } catch (Exception e) {
            log.error("❌ 跨表同步失败 - 单位: {}, 错误: {}", reportUnit, e.getMessage());
        }
    }

    // ==================== 🆕 优化：标准化和校验方法 ====================

    /**
     * 🎯 省市标准化处理（优化版）

     * ==================== 方法说明 ====================
     * 对用户输入的省市信息进行标准化处理，确保数据格式统一。
     * 新增：使用精确的标准化逻辑，避免误匹配。

     * ==================== 处理规则 ====================
     * 1. 省份标准化：使用精确的简称到全称映射
     * 2. 城市标准化：支持多种行政区划类型的标准化
     * 3. 格式统一：确保所有省市名称使用标准格式
     */
//    private void standardizeProvinceCity(CyberAsset asset) {
//        String originalProvince = asset.getProvince();
//        String originalCity = asset.getCity();
//
//        // 🆕 优化：分别标准化省份和城市
//        String standardizedProvince = standardizeProvinceName(originalProvince);
//        if (!originalProvince.equals(standardizedProvince)) {
//            log.debug("🏷️ 省份标准化: '{}' → '{}'", originalProvince, standardizedProvince);
//            asset.setProvince(standardizedProvince);
//        }
//
//        String standardizedCity = standardizeCityName(originalCity);
//        if (!originalCity.equals(standardizedCity)) {
//            log.debug("🏷️ 城市标准化: '{}' → '{}'", originalCity, standardizedCity);
//            asset.setCity(standardizedCity);
//        }
//    }
    /**
     * 🎯 省市标准化处理（空指针安全版）
     */
    private void standardizeProvinceCity(CyberAsset asset) {
        String originalProvince = asset.getProvince();
        String originalCity = asset.getCity();

        // 🆕 修复：安全比较，避免空指针
        if (originalProvince != null) {
            String standardizedProvince = standardizeProvinceName(originalProvince);
            if (!Objects.equals(originalProvince, standardizedProvince)) {
                log.debug("🏷️ 省份标准化: '{}' → '{}'", originalProvince, standardizedProvince);
                asset.setProvince(standardizedProvince);
            }
        }

        if (originalCity != null) {
            String standardizedCity = standardizeCityName(originalCity);
            if (!Objects.equals(originalCity, standardizedCity)) {
                log.debug("🏷️ 城市标准化: '{}' → '{}'", originalCity, standardizedCity);
                asset.setCity(standardizedCity);
            }
        }
    }


    /**
     * 🏷️ 省份名称标准化（优化版）

     * ==================== 方法说明 ====================
     * 使用精确的简称到全称映射，确保省份名称格式统一。
     * 避免使用包含匹配导致的误匹配问题。

     * @param provinceName 原始省份名称
     * @return 标准化后的省份名称
     */
    private String standardizeProvinceName(String provinceName) {
        if (!StringUtils.hasText(provinceName)) {
            return provinceName;
        }

        provinceName = provinceName.trim();

        // 1. 检查是否已经是标准省份名称
        for (String standardProvince : areaCacheTool.getAllProvinceNames()) {
            if (standardProvince.equals(provinceName)) {
                return provinceName; // 已经是标准格式
            }
        }

        // 2. 精确的简称到全称映射
        Map<String, String> provinceMapping = createProvinceMapping();
        if (provinceMapping.containsKey(provinceName)) {
            String standardized = provinceMapping.get(provinceName);
            log.debug("🏷️ 省份简称映射: '{}' → '{}'", provinceName, standardized);
            return standardized;
        }

        // 3. 使用简写匹配标准名称（兜底方案）
        for (String standardProvince : areaCacheTool.getAllProvinceNames()) {
            String standardAbbr = getProvinceAbbreviation(standardProvince);
            if (standardAbbr.equals(provinceName)) {
                log.debug("🏷️ 省份简写匹配: '{}' → '{}'", provinceName, standardProvince);
                return standardProvince;
            }
        }

        log.debug("⚠️ 无法标准化省份名称: {}", provinceName);
        return provinceName;
    }

    /**
     * 🏷️ 城市名称标准化（优化版）

     * ==================== 方法说明 ====================
     * 支持多种行政区划类型的标准化处理，确保城市名称格式统一。
     * 使用精确匹配，避免误匹配问题。

     * @param cityName 原始城市名称
     * @return 标准化后的城市名称
     */
    private String standardizeCityName(String cityName) {
        if (!StringUtils.hasText(cityName)) {
            return cityName;
        }

        cityName = cityName.trim();

        // 1. 检查是否已经是标准城市名称
        for (String standardCity : areaCacheTool.getAllCityNames()) {
            if (standardCity.equals(cityName)) {
                return cityName; // 已经是标准格式
            }
        }

        // 2. 使用简写匹配标准名称
        for (String standardCity : areaCacheTool.getAllCityNames()) {
            String standardAbbr = getCityAbbreviation(standardCity);
            if (standardAbbr.equals(cityName)) {
                log.debug("🏷️ 城市简写匹配: '{}' → '{}'", cityName, standardCity);
                return standardCity;
            }
        }

        log.debug("⚠️ 无法标准化城市名称: {}", cityName);
        return cityName;
    }

    /**
     * 🆕 创建省份简称到全称的精确映射
     */
    private Map<String, String> createProvinceMapping() {
        Map<String, String> mapping = new HashMap<>();

        // 直辖市和自治区
        mapping.put("北京", "北京市");
        mapping.put("上海", "上海市");
        mapping.put("天津", "天津市");
        mapping.put("重庆", "重庆市");
        mapping.put("新疆", "新疆维吾尔自治区");
        mapping.put("广西", "广西壮族自治区");
        mapping.put("宁夏", "宁夏回族自治区");
        mapping.put("西藏", "西藏自治区");
        mapping.put("内蒙古", "内蒙古自治区");

        // 普通省份
        mapping.put("黑龙江", "黑龙江省");
        mapping.put("吉林", "吉林省");
        mapping.put("辽宁", "辽宁省");
        mapping.put("河北", "河北省");
        mapping.put("河南", "河南省");
        mapping.put("山东", "山东省");
        mapping.put("山西", "山西省");
        mapping.put("江苏", "江苏省");
        mapping.put("浙江", "浙江省");
        mapping.put("安徽", "安徽省");
        mapping.put("福建", "福建省");
        mapping.put("江西", "江西省");
        mapping.put("湖北", "湖北省");
        mapping.put("湖南", "湖南省");
        mapping.put("广东", "广东省");
        mapping.put("海南", "海南省");
        mapping.put("四川", "四川省");
        mapping.put("贵州", "贵州省");
        mapping.put("云南", "云南省");
        mapping.put("陕西", "陕西省");
        mapping.put("甘肃", "甘肃省");
        mapping.put("青海", "青海省");

        return mapping;
    }

//    /**
//     * 🔍 省市字段严格校验
//
//     * ==================== 方法说明 ====================
//     * 对省市数据进行严格的合法性和规范性校验。
//     * 确保省市信息符合业务规则，防止无效数据入库。
//
//     * ==================== 校验规则 ====================
//     * 1. 省份不能为空，必须是34个标准省份或"未知"
//     * 2. 城市不能为空且不能是无效字符
//
//     * ==================== 技术实现 ====================
//     * 使用预定义的标准省份列表进行有效性校验
//     * 严格的空值和格式校验
//     * 详细的错误信息提示
//     *
//     * @param province 省份
//     * @param city 城市
//     * @throws RuntimeException 当省市数据不符合规范时抛出异常
//     *
//     * @apiNote 严格的校验确保数据质量，为后续的数据分析和统计提供可靠基础
//     */
//    private void validateProvinceCity(String province, String city) {
//        log.debug("🔍 开始省市字段校验 - 省: {}, 市: {}", province, city);
//
//        // 1. 省份非空校验
//        if (!StringUtils.hasText(province)) {
//            throw new RuntimeException("省份不能为空");
//        }
//
//        // 2. 省份有效性校验（34个标准省份 + "未知"）
//        List<String> validProvinces = Arrays.asList(
//                "北京市", "天津市", "河北省", "山西省", "内蒙古自治区", "辽宁省", "吉林省", "黑龙江省",
//                "上海市", "江苏省", "浙江省", "安徽省", "福建省", "江西省", "山东省", "河南省", "湖北省",
//                "湖南省", "广东省", "广西壮族自治区", "海南省", "重庆市", "四川省", "贵州省", "云南省",
//                "西藏自治区", "陕西省", "甘肃省", "青海省", "宁夏回族自治区", "新疆维吾尔自治区", "台湾省",
//                "香港特别行政区", "澳门特别行政区", "未知"
//        );
//
//        if (!validProvinces.contains(province)) {
//            throw new RuntimeException("省份必须是34个标准省份之一或'未知'，当前省份: " + province);
//        }
//
//        // 3. 城市非空校验
//        if (!StringUtils.hasText(city)) {
//            throw new RuntimeException("城市不能为空");
//        }
//
//        // 4. 城市有效性校验（不能是纯空格）
//        if (city.trim().isEmpty()) {
//            throw new RuntimeException("城市不能为纯空格");
//        }
//
//        log.debug("✅ 省市字段校验通过 - 省: {}, 市: {}", province, city);
//    }

    // ==================== 🆕 优化：简写处理方法（完整版） ====================

    /**
     * 🏷️ 获取省份名称的简写形式

     * ==================== 方法说明 ====================
     * 从完整的省份名称中提取核心简写名称，便于匹配和标准化处理。
     * 支持所有类型的省级行政区划名称。

     * @param province 完整的省份名称
     * @return 去除后缀的省份简写名称
     */
    private String getProvinceAbbreviation(String province) {
        if (!StringUtils.hasText(province)) {
            return province;
        }

        return province.replace("省", "")
                .replace("自治区", "")
                .replace("壮族自治区", "")
                .replace("维吾尔自治区", "")
                .replace("回族自治区", "")
                .replace("特别行政区", "")
                .replace("市", ""); // 处理直辖市
    }

    /**
     * 🏷️ 获取城市名称的简写形式（完整版）

     * ==================== 方法说明 ====================
     * 从完整的行政区划名称中提取核心简写名称，便于在单位名称中进行匹配。
     * 支持处理所有类型的行政区划，包括地级市、县级市、自治州、地区、盟、特别行政区等。

     * ==================== 处理规则 ====================
     * 1. 特殊自治州映射：对常见自治州使用习惯简写
     * 2. 后缀去除规则：按行政区划类型去除相应后缀
     *    - 市：去除"市"后缀
     *    - 自治州：去除"自治州"后缀
     *    - 地区：去除"地区"后缀
     *    - 盟：去除"盟"后缀
     *    - 特别行政区：去除"特别行政区"后缀

     * @param city 完整的城市/行政区划名称
     * @return 处理后的简写名称，如无法处理则返回原名称
     */
    private String getCityAbbreviation(String city) {
        // 1. 空值检查：确保输入有效
        if (!StringUtils.hasText(city)) {
            return city;
        }

        // 2. 特殊自治州映射：对常见自治州使用习惯简写
        Map<String, String> specialAutonomousMapping = new HashMap<>();
        specialAutonomousMapping.put("湘西土家族苗族自治州", "湘西");
        specialAutonomousMapping.put("延边朝鲜族自治州", "延边");
        specialAutonomousMapping.put("恩施土家族苗族自治州", "恩施");
        specialAutonomousMapping.put("阿坝藏族羌族自治州", "阿坝");
        specialAutonomousMapping.put("甘孜藏族自治州", "甘孜");
        specialAutonomousMapping.put("凉山彝族自治州", "凉山");
        specialAutonomousMapping.put("黔西南布依族苗族自治州", "黔西南");
        specialAutonomousMapping.put("黔东南苗族侗族自治州", "黔东南");
        specialAutonomousMapping.put("黔南布依族苗族自治州", "黔南");
        specialAutonomousMapping.put("楚雄彝族自治州", "楚雄");
        specialAutonomousMapping.put("红河哈尼族彝族自治州", "红河");
        specialAutonomousMapping.put("文山壮族苗族自治州", "文山");
        specialAutonomousMapping.put("西双版纳傣族自治州", "西双版纳");
        specialAutonomousMapping.put("大理白族自治州", "大理");
        specialAutonomousMapping.put("德宏傣族景颇族自治州", "德宏");
        specialAutonomousMapping.put("怒江傈僳族自治州", "怒江");
        specialAutonomousMapping.put("迪庆藏族自治州", "迪庆");

        // 检查特殊映射
        if (specialAutonomousMapping.containsKey(city)) {
            String abbreviation = specialAutonomousMapping.get(city);
            log.debug("🔤 特殊自治州简写映射: '{}' -> '{}'", city, abbreviation);
            return abbreviation;
        }

        // 3. 常规后缀处理：按行政区划类型去除相应后缀
        // 注意：按后缀长度从长到短处理，避免错误匹配

        // 3.1 特别行政区处理
        if (city.endsWith("特别行政区")) {
            return city.replace("特别行政区", "");
        }

        // 3.2 自治州处理（兜底，处理不在特殊映射中的自治州）
        if (city.endsWith("自治州")) {
            return city.replace("自治州", "");
        }

        // 3.3 地区处理
        if (city.endsWith("地区")) {
            return city.replace("地区", "");
        }

        // 3.4 盟处理
        if (city.endsWith("盟")) {
            return city.replace("盟", "");
        }

        // 3.5 市处理（最后处理，因为"市"可能出现在其他类型中）
        if (city.endsWith("市")) {
            return city.replace("市", "");
        }

        // 4. 无法处理的情况：返回原名称
        log.debug("⚠️ 无法简写的城市名称: '{}'，保持原值", city);
        return city;
    }

    // ==================== 其他业务方法 ====================

    /**
     * ✅ 统一的业务字段校验方法

     * ==================== 方法说明 ====================
     * 统一调用所有业务字段的校验方法，确保数据的完整性。
     * 提供统一的校验入口，便于维护和扩展。
     *
     * @param asset 网信资产对象
     *
     * @apiNote 此方法封装了所有业务校验逻辑，确保校验的完整性
     */
    private void validateBusinessFields(CyberAsset asset) {
        validateReportUnit(asset);
        validateCategory(asset);
        validateAssetName(asset);
        validateAssetContent(asset);
        validateActualQuantity(asset);
        validateUnit(asset);
        validateUnitPrice(asset);
        validatePutIntoUseDate(asset);
        validateUsedQuantity(asset);
        validateInventoryUnit(asset);
    }

    /**
     * 删除网信基础资产（集成上报单位表同步）
     * 功能概述：
     * 本方法用于删除单条网信资产记录，包含资产存在性校验、数据删除和上报单位表同步功能。
     * 网信资产表与其他资产表的主要区别：有省市字段，需要同时维护自身字段和上报单位表。

     * 核心流程：
     * 1. 资产存在性校验阶段 → 2. 数据删除阶段 → 3. 上报单位表同步阶段

     * 业务规则：
     * - 必须先查询资产是否存在，获取完整的资产信息（包括省市）
     * - 删除操作必须同步更新上报单位表的状态标志
     * - 使用事务确保数据一致性，任何步骤失败都会回滚

     * 同步逻辑：
     * - 调用 provinceAutoFillTool.syncReportUnit 方法
     * - 设置 isDelete=true，表示删除场景
     * - 如果该单位不再有网信资产，系统会自动将网信资产状态标志设为0
     * - 使用资产中的省份信息进行同步，确保数据准确性

     * 事务管理：
     * - 使用@Transactional注解确保操作原子性
     * - 任何校验失败或删除失败都会回滚整个事务
     * - rollbackFor = Exception.class 确保所有异常都会触发回滚

     * 适用场景：
     * - 前端手动删除网信资产
     * - 需要完整事务管理和上报单位同步的业务场景
     * - 单条记录删除操作

     * 注意事项：
     * - 删除前必须查询资产信息，获取上报单位名称和省市信息用于同步
     * - 删除后需要同步上报单位表，确保状态标志准确
     * - 如果资产不存在，抛出明确的业务异常信息
     * - 网信资产有省市字段，同步时需要传递省份参数
     *
     * @param id 网信资产主键ID，必填参数
     * @throws RuntimeException 当资产不存在或删除失败时抛出业务异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(String id) {
        log.info("🚀 开始删除网信资产，ID：{}", id);

        // ==================== 1. 资产存在性校验阶段 ====================

        // 1.1 根据ID查询资产信息（包含省市字段）
        CyberAsset asset = baseMapper.selectById(id);
        if (asset == null) {
            log.error("❌ 网信资产不存在，删除失败，ID：{}", id);
            throw new RuntimeException("网信资产不存在，ID：" + id);
        }

        // 1.2 获取上报单位和省市信息，用于后续同步操作
        String reportUnit = asset.getReportUnit();
        String province = asset.getProvince();
        log.debug("📋 找到待删除网信资产 - ID: {}, 上报单位: {}, 省份: {}, 资产名称: {}",
                id, reportUnit, province, asset.getAssetName());

        // ==================== 2. 数据删除阶段 ====================

        // 2.1 执行物理删除操作
        int deleteCount = baseMapper.deleteById(id);
        if (deleteCount == 0) {
            log.error("❌ 网信资产删除失败，可能已被其他操作删除，ID：{}", id);
            throw new RuntimeException("删除网信资产失败，ID：" + id);
        }

        log.info("✅ 删除网信资产成功，ID：{}，资产名称：{}", id, asset.getAssetName());

        // ==================== 3. 上报单位表同步阶段 ====================

        // 3.1 同步上报单位表状态（删除场景）
        // 作用：更新上报单位表中该单位的网信资产状态标志
        // 逻辑：如果该单位不再有网信资产，系统会自动将cyber_asset_status设为0
        provinceAutoFillTool.syncReportUnit(
                reportUnit,           // 上报单位名称（从已删除资产获取）
                province,             // 网信资产有省份字段，使用资产中的省份信息
                "cyber",              // 资产类型：网信资产
                true                  // isDelete=true：删除场景，触发状态标志更新
        );
        log.debug("🔄 网信资产删除完成，已同步上报单位表状态 - 单位: {}, 省份: {}", reportUnit, province);
    }

    @Override
    public boolean checkCategoryMatch(String categoryCode, String assetCategory) {
        String legalCategory = CATEGORY_MAP.get(categoryCode);
        if (legalCategory == null) {
            log.warn("网信资产分类编码非法：{}（无对应标准分类）", categoryCode);
            return false;
        }
        return legalCategory.trim().equals(assetCategory.trim());
    }

    @Override
    public void validateUsedQuantity(Integer usedQuantity, Integer actualQuantity) {
        if (usedQuantity == null) {
            throw new RuntimeException("网信资产已用数量不能为空");
        }
        if (actualQuantity == null) {
            throw new RuntimeException("网信资产实有数量不能为空");
        }
        if (usedQuantity < 0) {
            throw new RuntimeException("已用数量不能为负数（当前值：" + usedQuantity + "）");
        }
        if (actualQuantity < 0) {
            throw new RuntimeException("实有数量不能为负数（当前值：" + actualQuantity + "）");
        }
        if (usedQuantity > actualQuantity) {
            throw new RuntimeException("已用数量超限！已用：" + usedQuantity + "，实有：" + actualQuantity);
        }
    }

    // ============================ 私有工具方法 ============================

    /**
     * 校验资产ID格式（数字+字母组合，移除长度限制）
     */
    @SuppressWarnings("all")
    private boolean isValidAssetId(String id) {
        if (!StringUtils.hasText(id)) {
            return false;
        }

        // 只允许数字和字母，移除长度限制
        return id.matches("^[a-zA-Z0-9]+$");
    }

    // ============================ 新增方法实现（接口方法） ============================

    @Override
    public void addCyberAsset(CyberAsset asset) {
        // 直接调用原有的 add 方法，因为 add 方法已经集成了省市自动填充和上报单位同步
        add(asset);
        log.debug("通过 addCyberAsset 方法新增网信资产成功，ID：{}", asset.getId());
    }

    @Override
    public void updateCyberAsset(CyberAsset asset) {
        // 直接调用原有的 update 方法，因为 update 方法已经集成了省市自动填充和上报单位同步
        update(asset);
        log.debug("通过 updateCyberAsset 方法修改网信资产成功，ID：{}", asset.getId());
    }

    @Override
    public void deleteCyberAsset(String id) {
        // 直接调用原有的 remove 方法，因为 remove 方法已经集成了上报单位同步
        remove(id);
        log.debug("通过 deleteCyberAsset 方法删除网信资产成功，ID：{}", id);
    }

    // ============================ 新增额外接口 ============================
    /**
     * 接口1
     * 统计网信资产数量
     */
    @Override
    public long count() {
        // 使用MyBatis-Plus的count方法
        return this.getBaseMapper().selectCount(null);
    }

    /**
     * 接口2
     * 返回快速查询结果
     */
    @Override
    public Page<CyberAsset> queryByCategory(Page<CyberAsset> page, String categoryCode, String assetCategory) {
        return this.getBaseMapper().queryByCategory(page, categoryCode, assetCategory);
    }

    /**
     * 接口3
     * 实现按上报单位查询网信资产
     * 调用Mapper层的queryByReportUnit方法执行SQL查询
     */
    @Override
    public Page<CyberAsset> queryByReportUnit(Page<CyberAsset> page, String reportUnit) {
        return this.getBaseMapper().queryByReportUnit(page, reportUnit);
    }

// ==================== 新增：接口4相关方法实现 ====================

    @Override
    public List<Map<String, Object>> getProvinceUnitStats() {
        /**
         * 实现网信资产表省份单位统计（新逻辑：关联report_unit表）
         * 设计考虑：虽然cyber_asset表有province列，但为了保持一致性
         * 统一通过关联report_unit表获取省份信息

         * SQL执行逻辑：
         *   SELECT ru.province, COUNT(DISTINCT ca.report_unit) as count
         *   FROM cyber_asset ca
         *   INNER JOIN report_unit ru ON ca.report_unit = ru.report_unit
         *   WHERE ru.province IS NOT NULL AND ru.province != ''
         *   GROUP BY ru.province
         *   ORDER BY count DESC

         * 优势：
         * - 统一了三个资产表的省份数据来源
         * - 避免使用可能为空或不准确的资产表province字段
         * - 确保统计结果的可比性
         */
        return this.getBaseMapper().selectProvinceUnitStats();
    }

// ============================ 🆕 新增方法（清空再导入专用） ============================

    /**
     * 清空网信资产表并重置上报单位表状态（导入专用）
     * 🎯 核心操作：
     * 1. 清空cyber_asset表的所有数据
     * 2. 将report_unit表中source_table_cyber_asset字段全部设为0

     * 💡 重要说明：
     * - 只重置网信资产状态，不影响其他资产表的状态
     * - 不清空report_unit表的其他字段（省市信息等）
     * - 使用事务确保数据一致性

     * 🚨 风险提示：
     * - 此操作会永久删除所有网信资产数据
     * - 只能在导入前调用，确保数据备份
     *
     * @throws RuntimeException 当清空操作失败时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearCyberTableAndResetStatus() {
        log.info("🗑️ 开始清空网信资产表并重置上报单位表状态...");

        try {
            // 1. 清空cyber_asset表的所有数据
            int deletedCount = baseMapper.delete(null); // 删除所有记录
            log.info("✅ 清空网信资产表完成，共删除{}条记录", deletedCount);

            // 2. 重置report_unit表中网信资产状态为0
            int updatedCount = baseMapper.resetCyberAssetStatus();
            log.info("✅ 重置上报单位表网信资产状态完成，共更新{}条记录", updatedCount);

            log.info("🎉 网信资产表和状态重置完成");
        } catch (Exception e) {
            log.error("❌ 清空网信资产表失败: {}", e.getMessage(), e);
            throw new RuntimeException("清空网信资产表失败: " + e.getMessage());
        }
    }

    /**
     * 批量保存网信资产并同步省市信息（导入专用-完整16种情况处理版）
     *
     * ==================== 核心特性 ====================
     * ✅ 完整的16种输入情况覆盖
     * ✅ 基于状态判断的精确处理逻辑
     * ✅ 完全依赖单位名称智能推导，不查询空的上报单位表
     * ✅ 确保数据完整性和同步正确性
     *
     * ==================== 16种情况处理逻辑 ====================
     * 场景1：Excel省市都有效且一致 → 使用Excel值（标准化）
     * 场景2：Excel省市都为空 → 单位推导
     * 场景3：Excel省市部分有效 → 分别处理（12种子场景）
     * 场景4：Excel省市都未知或都无效 → 省市都设为未知
     * 场景5：Excel省市不一致 → 市设为未知，省保留
     *
     * @param assets 校验通过的网信基础资产列表
     * @throws RuntimeException 当批量保存失败时抛出业务异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveForImport(List<CyberAsset> assets) {
        if (assets == null || assets.isEmpty()) {
            log.info("ℹ️ 批量保存网信资产：无数据需要保存");
            return;
        }

        log.info("💾 开始批量保存网信资产并同步省市信息，共{}条数据", assets.size());

        try {
            // 1. 批量智能处理省市信息（基于状态判断的完整场景覆盖）
            processProvinceCityForBatchImport(assets);

            // 2. 批量保存到cyber_asset表
            boolean saveResult = saveBatch(assets);
            if (!saveResult) {
                throw new RuntimeException("批量保存网信资产失败");
            }
            log.info("✅ 批量保存网信资产成功，共{}条", assets.size());

            // 3. 按上报单位分组，用于批量同步
            Map<String, List<CyberAsset>> unitGroupedAssets = assets.stream()
                    .collect(Collectors.groupingBy(CyberAsset::getReportUnit));

            log.info("📊 按单位分组完成，共{}个不同单位", unitGroupedAssets.size());

            // 4. 批量同步上报单位表（网信资产特殊处理：有省市字段）
            List<ProvinceAutoFillTool.UnitSyncRequest> syncRequests = new ArrayList<>();
            for (Map.Entry<String, List<CyberAsset>> entry : unitGroupedAssets.entrySet()) {
                String unitName = entry.getKey();
                // 获取该单位的第一个资产的省市信息（相同单位的省市应该一致）
                CyberAsset firstAsset = entry.getValue().get(0);
                syncRequests.add(new ProvinceAutoFillTool.UnitSyncRequest(
                        unitName,
                        firstAsset.getProvince(),  // 使用网信资产的省市信息
                        "cyber",                   // 资产类型标识
                        false                      // 新增模式
                ));
            }

            // 执行批量同步
            provinceAutoFillTool.batchSyncReportUnits(syncRequests);

            // 🆕 修改：无条件跨表同步到数据内容资产表（跨表同步）
            log.info("🔄 开始无条件跨表同步到数据内容资产表");
            int crossSyncCount = 0;
            for (Map.Entry<String, List<CyberAsset>> entry : unitGroupedAssets.entrySet()) {
                String unitName = entry.getKey();
                CyberAsset firstAsset = entry.getValue().get(0);
                String province = firstAsset.getProvince();
                String city = firstAsset.getCity();

                // 🆕 修改：无论省市是什么，只要数据内容表存在就同步
                if (checkUnitExistsInDataContentTable(unitName)) {
                    syncToDataTable(unitName, province, city);
                    crossSyncCount++;
                    log.debug("✅ 跨表同步完成 - 单位: {}, 省市: {}-{}", unitName, province, city);
                }
            }

            log.info("🎉 网信资产批量导入完成，涉及{}个单位", unitGroupedAssets.size());

        } catch (Exception e) {
            log.error("❌ 批量保存网信资产失败: {}", e.getMessage(), e);
            throw new RuntimeException("批量保存网信资产失败: " + e.getMessage());
        }
    }

    // ==================== 🆕 新增：导入专用枚举和状态方法 ====================
    /**
     * 🎯 统一的状态判断方法（导入专用）
     */
    private enum FieldState {
        EMPTY, UNKNOWN, VALID, INVALID
    }

    /**
     * 🎯 处理场景枚举
     */
    private enum ProcessScene {
        SCENE_1("Excel省市有效且一致"),
        SCENE_2("Excel省市都为空"),
        SCENE_3("Excel省市部分有效"),
        SCENE_4("Excel省市都未知或都无效"),
        SCENE_5("Excel省市不一致");

        private final String description;

        ProcessScene(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 🎯 获取省份状态（基于您现有的标准化方法）
     */
    private FieldState getProvinceState(String province) {
        if (!StringUtils.hasText(province)) {
            return FieldState.EMPTY;
        }
        if ("未知".equals(province)) {
            return FieldState.UNKNOWN;
        }
        // 使用您现有的有效性检查方法
        if (isProvinceValid(province)) {
            return FieldState.VALID;
        }
        return FieldState.INVALID;
    }

    /**
     * 🎯 获取城市状态（基于您现有的标准化方法）
     */
    private FieldState getCityState(String city) {
        if (!StringUtils.hasText(city)) {
            return FieldState.EMPTY;
        }
        if ("未知".equals(city)) {
            return FieldState.UNKNOWN;
        }
        // 使用您现有的有效性检查方法
        if (isCityValid(city)) {
            return FieldState.VALID;
        }
        return FieldState.INVALID;
    }


    /**
     * 🎯 批量处理省市信息（基于状态判断的完整16种情况处理）
     *
     * ==================== 核心特性 ====================
     * ✅ 完整的16种输入情况覆盖
     * ✅ 使用您现有的简写识别和标准化方法
     * ✅ 基于状态判断的精确处理逻辑
     * ✅ 确保除"未知"外的省市都经过标准化
     *
     * ==================== 处理流程 ====================
     * 1. 标准化处理（使用您现有的standardizeProvinceCity方法）
     * 2. 状态判断（区分空、未知、有效、无效）
     * 3. 场景识别和处理（16种情况）
     * 4. 结果标准化（确保输出格式统一）
     */
    private void processProvinceCityForBatchImport(List<CyberAsset> assets) {
        log.info("🔄 开始批量处理网信资产省市信息，共{}条数据", assets.size());

        // 统计计数器
        int scene1Count = 0, scene2Count = 0, scene3Count = 0,
                scene4Count = 0, scene5Count = 0;

        for (CyberAsset asset : assets) {
            String originalProvince = asset.getProvince();
            String originalCity = asset.getCity();
            String unitName = asset.getReportUnit();

            // 🎯 第一步：使用您现有的标准化方法（包含简写识别）
            standardizeProvinceCity(asset);

            // 获取标准化后的值
            String processedProvince = asset.getProvince();
            String processedCity = asset.getCity();

            // 🎯 第二步：状态判断
            FieldState provinceState = getProvinceState(processedProvince);
            FieldState cityState = getCityState(processedCity);

            log.debug("🔍 状态判断 - 单位: {}, 省状态: {}, 市状态: {}",
                    unitName, provinceState, cityState);

            // 🎯 第三步：场景识别和处理
            ProcessScene scene = identifyProcessScene(provinceState, cityState);

            switch (scene) {
                case SCENE_1:
                    scene1Count++;
                    handleScene1(asset, processedProvince, processedCity);
                    break;
                case SCENE_2:
                    scene2Count++;
                    handleScene2(asset, unitName);
                    break;
                case SCENE_3:
                    scene3Count++;
                    handleScene3(asset, provinceState, cityState, processedProvince, processedCity, unitName);
                    break;
                case SCENE_4:
                    scene4Count++;
                    handleScene4(asset);
                    break;
                case SCENE_5:
                    scene5Count++;
                    handleScene5(asset, processedProvince);
                    break;
            }

            // 🎯 第四步：最终标准化（确保除"未知"外的值都标准化）
            applyFinalStandardization(asset);
        }

        log.info("📊 网信资产批量省市处理统计 - 场景1: {}, 场景2: {}, 场景3: {}, 场景4: {}, 场景5: {}",
                scene1Count, scene2Count, scene3Count, scene4Count, scene5Count);
    }

    /**
     * 🎯 场景识别方法
     */
    private ProcessScene identifyProcessScene(FieldState provinceState, FieldState cityState) {
        // 场景2：都为空
        if (provinceState == FieldState.EMPTY && cityState == FieldState.EMPTY) {
            return ProcessScene.SCENE_2;
        }

        // 场景4：都未知或都无效
        if ((provinceState == FieldState.UNKNOWN && cityState == FieldState.UNKNOWN) ||
                (provinceState == FieldState.INVALID && cityState == FieldState.INVALID)) {
            return ProcessScene.SCENE_4;
        }

        // 场景1：都有效且一致（需要进一步检查一致性）
        if (provinceState == FieldState.VALID && cityState == FieldState.VALID) {
            return ProcessScene.SCENE_1; // 一致性检查在handleScene1中处理
        }

        // 场景5：都有效但不一致
        if (provinceState == FieldState.VALID && cityState == FieldState.VALID) {
            return ProcessScene.SCENE_5; // 实际不会执行到这里，因为上面已经返回SCENE_1
        }

        // 场景3：其他所有情况
        return ProcessScene.SCENE_3;
    }

    // ==================== 🆕 新增：各场景处理方法 ====================

    /**
     * 🎯 场景1：Excel省市都有效且一致 → 使用Excel值（标准化）
     */
    private void handleScene1(CyberAsset asset, String province, String city) {
        log.debug("✅ 场景1 - Excel省市有效且一致: {}-{}", province, city);

        // 检查一致性
        if (!isProvinceCityConsistent(province, city)) {
            // 如果不一致，转为场景5处理
            log.debug("⚠️ 场景1转为场景5：省市不一致");
            handleScene5(asset, province);
            return;
        }

        // 都有效且一致，直接使用标准化后的值（不需要额外处理）
        log.debug("✅ 场景1完成 - 使用标准化值: {}-{}", province, city);
    }

    /**
     * 🎯 场景2：Excel省市都为空 → 单位推导
     */
    private void handleScene2(CyberAsset asset, String unitName) {
        log.debug("🔍 场景2 - Excel省市都为空，单位推导: {}", unitName);
        useToolToDeriveProvinceCity(asset, unitName);
    }

    /**
     * 🎯 场景4：Excel省市都未知或都无效 → 省市都设为未知
     */
    private void handleScene4(CyberAsset asset) {
        log.debug("❌ 场景4 - Excel省市都未知或都无效，设为未知");
        asset.setProvince("未知");
        asset.setCity("未知");
    }

    /**
     * 🎯 场景5：Excel省市不一致 → 市设为未知，省保留
     */
    private void handleScene5(CyberAsset asset, String province) {
        log.debug("⚠️ 场景5 - Excel省市不一致，市设为未知，省保留: {}", province);
        asset.setProvince(province); // 保留省
        asset.setCity("未知"); // 市设为未知
    }

    /**
     * 🎯 场景3：Excel省市部分有效（完整12种情况处理）
     */
    private void handleScene3(CyberAsset asset, FieldState provinceState, FieldState cityState,
                              String processedProvince, String processedCity, String unitName) {
        log.debug("🔧 场景3处理 - 省状态: {}, 市状态: {}", provinceState, cityState);

        // 🎯 情况3：空-有效 → 用有效的市推导省
        if (provinceState == FieldState.EMPTY && cityState == FieldState.VALID) {
            log.debug("🎯 情况3 - 空-有效 → 用有效的市推导省: {}", processedCity);
            String derivedProvince = deriveProvinceFromCity(processedCity);
            if (derivedProvince != null && !"未知".equals(derivedProvince)) {
                asset.setProvince(derivedProvince);
                asset.setCity(processedCity);
                log.debug("✅ 推导成功: {} → {}", processedCity, derivedProvince);
            } else {
                // 推导失败，使用单位推导
                useToolToDeriveProvinceCity(asset, unitName);
            }
            return;
        }

        // 🎯 情况4：空-无效 → 单位推导省，市设为未知
        if (provinceState == FieldState.EMPTY && cityState == FieldState.INVALID) {
            log.debug("🎯 情况4 - 空-无效 → 单位推导省，市设为未知");
            useToolToDeriveProvince(asset, unitName);
            asset.setCity("未知");
            return;
        }

        // 🎯 情况5：未知-空 → 省为未知，市也为未知
        if (provinceState == FieldState.UNKNOWN && cityState == FieldState.EMPTY) {
            log.debug("🎯 情况5 - 未知-空 → 省为未知，市也为未知");
            asset.setProvince("未知");
            asset.setCity("未知");
            return;
        }

        // 🎯 情况8：未知-无效 → 市无效直接设为未知
        if (provinceState == FieldState.UNKNOWN && cityState == FieldState.INVALID) {
            log.debug("🎯 情况8 - 未知-无效 → 市无效直接设为未知");
            asset.setProvince("未知");
            asset.setCity("未知");
            return;
        }

        // 🎯 情况9：有效-空 → 省有效，市填该省首府
        if (provinceState == FieldState.VALID && cityState == FieldState.EMPTY) {
            log.debug("🎯 情况9 - 有效-空 → 省有效，市填该省首府: {}", processedProvince);
            String capital = getCapitalByProvince(processedProvince);
            if (capital != null && !"未知".equals(capital)) {
                asset.setProvince(processedProvince);
                asset.setCity(capital);
                log.debug("✅ 设置首府成功: {} → {}", processedProvince, capital);
            } else {
                asset.setProvince(processedProvince);
                asset.setCity("未知");
                log.debug("❌ 获取首府失败，市设为未知");
            }
            return;
        }

        // 🎯 情况13：无效-空 → 省无效为未知，市也为未知
        if (provinceState == FieldState.INVALID && cityState == FieldState.EMPTY) {
            log.debug("🎯 情况13 - 无效-空 → 省无效为未知，市也为未知");
            asset.setProvince("未知");
            asset.setCity("未知");
            return;
        }

        // 🎯 情况14：无效-未知 → 省无效为未知，市也为未知
        if (provinceState == FieldState.INVALID && cityState == FieldState.UNKNOWN) {
            log.debug("🎯 情况14 - 无效-未知 → 省无效为未知，市也为未知");
            asset.setProvince("未知");
            asset.setCity("未知");
            return;
        }

        // 🎯 其他情况处理
        handleOtherScene3Cases(asset, provinceState, cityState, processedProvince, processedCity, unitName);
    }

    /**
     * 🎯 场景3的其他情况处理
     */
    private void handleOtherScene3Cases(CyberAsset asset, FieldState provinceState, FieldState cityState,
                                        String processedProvince, String processedCity, String unitName) {
        // 🎯 情况2：空-未知 → 单位推导省，市保留未知
        if (provinceState == FieldState.EMPTY && cityState == FieldState.UNKNOWN) {
            log.debug("🎯 情况2 - 空-未知 → 单位推导省，市保留未知");
            useToolToDeriveProvince(asset, unitName);
            asset.setCity("未知");
            return;
        }

        // 🎯 情况7：未知-有效 → 用有效的市推导省
        if (provinceState == FieldState.UNKNOWN && cityState == FieldState.VALID) {
            log.debug("🎯 情况7 - 未知-有效 → 用有效的市推导省: {}", processedCity);
            String derivedProvince = deriveProvinceFromCity(processedCity);
            if (derivedProvince != null && !"未知".equals(derivedProvince)) {
                asset.setProvince(derivedProvince);
                asset.setCity(processedCity);
                log.debug("✅ 推导成功: {} → {}", processedCity, derivedProvince);
            } else {
                asset.setProvince("未知");
                asset.setCity("未知");
            }
            return;
        }

        // 🎯 情况10：有效-未知 → 省保留，市保留未知
        if (provinceState == FieldState.VALID && cityState == FieldState.UNKNOWN) {
            log.debug("🎯 情况10 - 有效-未知 → 省保留，市保留未知: {}", processedProvince);
            asset.setProvince(processedProvince);
            asset.setCity("未知");
            return;
        }

        // 🎯 情况15：无效-有效 → 用有效的市推导省
        if (provinceState == FieldState.INVALID && cityState == FieldState.VALID) {
            log.debug("🎯 情况15 - 无效-有效 → 用有效的市推导省: {}", processedCity);
            String derivedProvince = deriveProvinceFromCity(processedCity);
            if (derivedProvince != null && !"未知".equals(derivedProvince)) {
                asset.setProvince(derivedProvince);
                asset.setCity(processedCity);
                log.debug("✅ 推导成功: {} → {}", processedCity, derivedProvince);
            } else {
                asset.setProvince("未知");
                asset.setCity("未知");
            }
            return;
        }

        // 🎯 情况6：未知-未知 → 保留未知（理论上不会到这里，因为被场景4处理）
        if (provinceState == FieldState.UNKNOWN && cityState == FieldState.UNKNOWN) {
            log.debug("🎯 情况6 - 未知-未知 → 保留未知");
            // 不需要处理，保持原样
            return;
        }

        // 🎯 默认情况：单位推导
        log.debug("🔍 场景3默认情况 → 单位推导");
        useToolToDeriveProvinceCity(asset, unitName);
    }

    // ==================== 🆕 新增：导入专用辅助方法 ====================

    /**
     * 🎯 仅推导省份（保持城市不变）
     */
    private void useToolToDeriveProvince(CyberAsset asset, String unitName) {
        String originalCity = asset.getCity();

        HasReportUnitAndProvince tempAsset = new HasReportUnitAndProvince() {
            @Override public String getReportUnit() { return unitName; }
            @Override public String getProvince() { return asset.getProvince(); }
            @Override public void setProvince(String province) { asset.setProvince(province); }
            @Override public String getCity() { return originalCity; }
            @Override public void setCity(String city) { /* 不设置城市 */ }
        };

        provinceAutoFillTool.fillAssetProvinceCity(tempAsset, false);
        log.debug("🤖 仅推导省份完成 - 单位: {}, 省份: {}", unitName, asset.getProvince());
    }

    /**
     * 🎯 根据省份获取首府城市（使用您现有的AreaCacheTool）
     */
    private String getCapitalByProvince(String province) {
        try {
            String standardizedProvince = standardizeProvinceName(province);
            String capital = areaCacheTool.getCapitalByProvinceName(standardizedProvince);
            if (StringUtils.hasText(capital) && !"未知".equals(capital)) {
                return capital;
            }
        } catch (Exception e) {
            log.error("❌ 获取省份首府失败: {}", province, e);
        }
        return "未知";
    }

    /**
     * 🎯 最终标准化处理（确保除"未知"外的值都标准化）
     */
    private void applyFinalStandardization(CyberAsset asset) {
        // 只有非"未知"的值才需要标准化
        if (!"未知".equals(asset.getProvince())) {
            String standardizedProvince = standardizeProvinceName(asset.getProvince());
            if (!Objects.equals(asset.getProvince(), standardizedProvince)) {
                log.debug("🏷️ 最终省份标准化: '{}' → '{}'", asset.getProvince(), standardizedProvince);
                asset.setProvince(standardizedProvince);
            }
        }

        if (!"未知".equals(asset.getCity())) {
            String standardizedCity = standardizeCityName(asset.getCity());
            if (!Objects.equals(asset.getCity(), standardizedCity)) {
                log.debug("🏷️ 最终城市标准化: '{}' → '{}'", asset.getCity(), standardizedCity);
                asset.setCity(standardizedCity);
            }
        }
    }
    
    /**
     * 网信资产导出查询方法实现
     * 作用：根据前端传递的动态条件查询网信资产数据，用于导出功能
     * 特点：
     * - 支持任意条件组合，所有参数都是可选的
     * - 不分页查询，返回所有匹配的数据
     * - 复用现有的联合查询逻辑，确保查询条件一致性
     * - 包含完整的日志记录，便于问题排查和系统监控

     * 参数说明：
     * @param reportUnit 上报单位（可选）- 按单位筛选
     * @param province 省份（可选）- 按省份筛选
     * @param city 城市（可选）- 按城市筛选
     * @param categoryCode 分类编码（可选）- 按分类编码筛选
     * @param assetCategory 资产分类（可选）- 按资产分类筛选
     * @param quantityMin 实有数量最小值（可选）- 数量范围查询
     * @param quantityMax 实有数量最大值（可选）- 数量范围查询
     * @param usedQuantityMin 已用数量最小值（可选）- 已用数量范围查询
     * @param usedQuantityMax 已用数量最大值（可选）- 已用数量范围查询
     * @param startUseDateStart 投入使用日期开始（可选）- 日期范围查询
     * @param startUseDateEnd 投入使用日期结束（可选）- 日期范围查询
     * @param inventoryUnit 盘点单位（可选）- 按盘点单位筛选
     *
     * @return List<CyberAsset> 返回所有匹配的网信资产数据列表
     * 技术实现：
     * - 使用超大分页(1, Integer.MAX_VALUE)获取所有数据
     * - 复用combinedQuery方法，避免重复代码
     * - 动态条件处理由combinedQuery内部实现
     * - 完整的异常处理和日志记录
     */
    /**
     * 网信资产联合查询方法实现
     * 作用：根据动态条件分页查询网信资产数据
     * 注意：使用Java原生字符串判断，避免额外依赖
     */
    @Override
    public Page<CyberAsset> combinedQuery(Page<CyberAsset> pageInfo,
                                          String reportUnit, String province, String city,
                                          String categoryCode, String assetCategory, Integer quantityMin,
                                          Integer quantityMax, Integer usedQuantityMin, Integer usedQuantityMax,
                                          String startUseDateStart, String startUseDateEnd, String inventoryUnit) {
        try {
            log.info("执行网信资产联合查询 - 条件: reportUnit={}, province={}, city={}",
                    reportUnit, province, city);

            // 构建查询条件
            QueryWrapper<CyberAsset> queryWrapper = new QueryWrapper<>();

            // 动态添加查询条件 - 使用Java原生字符串判断
            if (reportUnit != null && !reportUnit.trim().isEmpty()) {
                queryWrapper.like("report_unit", reportUnit);
            }
            if (province != null && !province.trim().isEmpty()) {
                queryWrapper.eq("province", province);
            }
            if (city != null && !city.trim().isEmpty()) {
                queryWrapper.eq("city", city);
            }
            if (categoryCode != null && !categoryCode.trim().isEmpty()) {
                queryWrapper.like("category_code", categoryCode);
            }
            if (assetCategory != null && !assetCategory.trim().isEmpty()) {
                queryWrapper.like("asset_category", assetCategory);
            }
            if (quantityMin != null) {
                queryWrapper.ge("actual_quantity", quantityMin);
            }
            if (quantityMax != null) {
                queryWrapper.le("actual_quantity", quantityMax);
            }
            if (usedQuantityMin != null) {
                queryWrapper.ge("used_quantity", usedQuantityMin);
            }
            if (usedQuantityMax != null) {
                queryWrapper.le("used_quantity", usedQuantityMax);
            }
            if (startUseDateStart != null && !startUseDateStart.trim().isEmpty()) {
                queryWrapper.ge("put_into_use_date", startUseDateStart);
            }
            if (startUseDateEnd != null && !startUseDateEnd.trim().isEmpty()) {
                queryWrapper.le("put_into_use_date", startUseDateEnd);
            }
            if (inventoryUnit != null && !inventoryUnit.trim().isEmpty()) {
                queryWrapper.like("inventory_unit", inventoryUnit);
            }

            // 执行分页查询
            Page<CyberAsset> result = baseMapper.selectPage(pageInfo, queryWrapper);
            log.info("网信资产联合查询完成，共{}条数据", result.getRecords().size());

            return result;

        } catch (Exception e) {
            log.error("网信资产联合查询失败", e);
            throw new RuntimeException("查询失败: " + e.getMessage());
        }
    }

    // ============================== 新增：各省份资产统计方法 ==============================
    /**
     * 获取各省份网信资产统计概览
     * 作用：统计34个省份+"未知"的网信资产数量和占比

     * 核心逻辑：
     * 1. 直接使用网信资产表的province字段进行统计
     * 2. 统计每个省份的网信资产数量
     * 3. 计算每个省份网信资产占总量的百分比
     * 4. 包含"未知"省份的统计

     * 技术特点：
     * - 网信资产表有独立的province字段，无需关联查询
     * - 使用COALESCE处理null值，确保统计完整性
     * - 支持"未知"省份的准确统计
     *
     * @return 包含总数量和各省份统计的结果
     */
    @Override
    public Map<String, Object> getProvinceAssetOverview() {
        log.info("开始统计各省份网信资产数量和占比...");

        Map<String, Object> result = new HashMap<>();

        // 1. 获取网信资产总数
        long totalCyberCount = baseMapper.selectCount(null);
        result.put("totalCyberCount", totalCyberCount);

        // 2. 获取各省份网信资产统计
        List<Map<String, Object>> provinceStats = baseMapper.selectProvinceCyberStats();

        // 3. 转换为前端需要的格式并计算百分比
        List<Map<String, Object>> formattedStats = new ArrayList<>();
        for (Map<String, Object> stat : provinceStats) {
            String province = (String) stat.get("province");
            Long count = (Long) stat.get("count");

            Map<String, Object> formattedStat = new HashMap<>();
            formattedStat.put("province", province != null ? province : "未知");  // 修改：将"其他"改为"未知"
            formattedStat.put("cyberCount", count);

            // 计算百分比
            double percentage = totalCyberCount > 0 ?
                    (count.doubleValue() / totalCyberCount) * 100 : 0.0;
            formattedStat.put("cyberPercentage", Math.round(percentage * 10.0) / 10.0);

            formattedStats.add(formattedStat);
        }

        result.put("cyberProvinceStats", formattedStats);
        log.info("网信资产省份统计完成 - 总数: {}, 省份数量: {}", totalCyberCount, formattedStats.size());

        return result;
    }

    /**
     * 获取指定省份网信资产的资产分类细分
     * 作用：统计指定省份下各网信资产分类的数量和占比，确保返回完整的固定分类列表

     * 核心业务逻辑：
     * 1. 查询该省份网信资产总数
     * 2. 查询该省份各分类的实际统计数据
     * 3. 初始化所有网信资产固定分类映射表
     * 4. 创建包含所有固定分类的统计结果，默认数量为0
     * 5. 用实际查询结果更新对应分类的数量
     * 6. 计算各分类在该省份中的占比
     * 7. 返回完整的分类细分统计结果

     * 技术特点：
     * - 使用LinkedHashMap保持17个网信资产分类的顺序一致
     * - 网信资产表有独立的province字段，无需关联查询
     * - 确保返回所有固定分类，包括"其他网信基础资产"分类
     * - 基于该省份网信资产总数计算百分比
     *
     * @param province 省份名称
     * @return 包含分类细分的统计结果
     */
    @Override
    public Map<String, Object> getProvinceAssetCategoryDetail(String province) {
        log.info("开始统计省份网信资产分类细分 - 省份: {}", province);

        Map<String, Object> result = new HashMap<>();
        result.put("province", province);
        result.put("assetType", "cyber");

        // 1. 获取该省份网信资产总数
        Long provinceTotalCount = baseMapper.selectCyberCountByProvince(province);
        if (provinceTotalCount == null) provinceTotalCount = 0L;
        result.put("totalCount", provinceTotalCount);
        log.debug("省份网信资产总数统计完成 - 省份: {}, 总数: {}", province, provinceTotalCount);

        // 2. 获取该省份各资产分类的实际统计数据
        List<Map<String, Object>> categoryStats = baseMapper.selectCyberCategoryStatsByProvince(province);
        log.debug("获取到{}条网信资产分类统计记录", categoryStats.size());

        // 3. 定义所有网信资产分类的固定列表（使用分类名称作为标识）
        List<String> allCategoryNames = Arrays.asList(
                "自动电话号码", "人工电话号码", "保密电话号码", "移动手机号码",
                "有线信道", "光缆纤芯", "骨干网节点互联网络地址", "骨干网节点设备管理地址",
                "网络地址", "文电名录", "军事网络域名", "互联网域名",
                "无线电报代号", "电磁频谱", "数据中心计算资产", "数据中心存储资产", "其他网信基础资产"
        );

        // 4. 创建分类统计映射，初始化所有分类数量为0
        Map<String, Long> categoryCountMap = new LinkedHashMap<>(); // 使用LinkedHashMap保持顺序
        for (String categoryName : allCategoryNames) {
            categoryCountMap.put(categoryName, 0L);
        }
        log.debug("初始化了{}个网信资产分类", categoryCountMap.size());

        // 5. 填充实际统计数据
        for (Map<String, Object> stat : categoryStats) {
            String categoryName = (String) stat.get("asset_category"); // 直接获取分类名称
            Long count = (Long) stat.get("count");

            log.debug("处理网信资产分类统计 - 分类名称: {}, 数量: {}", categoryName, count);

            if (categoryName != null && categoryCountMap.containsKey(categoryName)) {
                categoryCountMap.put(categoryName, count);
                log.debug("成功更新网信资产分类统计 - 分类: {}, 数量: {}", categoryName, count);
            } else {
                log.warn("未知的网信资产分类名称: {}，已忽略", categoryName);
            }
        }

        // 6. 转换为前端需要的格式并计算百分比
        List<Map<String, Object>> formattedStats = new ArrayList<>();
        for (Map.Entry<String, Long> entry : categoryCountMap.entrySet()) {
            String categoryName = entry.getKey();
            Long count = entry.getValue();

            Map<String, Object> formattedStat = new HashMap<>();
            formattedStat.put("categoryName", categoryName);
            formattedStat.put("count", count);

            double percentage = provinceTotalCount > 0 ?
                    (count.doubleValue() / provinceTotalCount) * 100 : 0.0;
            formattedStat.put("percentage", Math.round(percentage * 10.0) / 10.0);

            formattedStats.add(formattedStat);
        }

        result.put("categoryStats", formattedStats);
        log.info("网信资产分类细分统计完成 - 省份: {}, 总数: {}, 分类数: {}",
                province, provinceTotalCount, formattedStats.size());

        return result;
    }

    /**
     * 根据资产分类按省份统计网信资产数量

     * 核心逻辑：
     * 1. 网信资产表有自身的province字段，可以直接使用该字段进行统计
     * 2. 统计指定资产分类下各省份的资产数量分布
     * 3. 处理省份为空的情况，统一归类为"未知"省份

     * 技术特点：
     * - 直接查询网信资产表的province字段，性能较好
     * - 支持所有网信资产分类的统计，如"自动电话号码"、"光缆纤芯"等

     * 业务价值：
     * - 分析网信基础设施的区域分布情况
     * - 为资源调配和规划提供数据支持
     *
     * @param assetCategory 资产分类名称，必须是有效的分类（如"自动电话号码"、"光缆纤芯"等）
     * @return Map<String, Long> 省份-数量映射，key为省份名称，value为该省份的资产数量
     * @throws RuntimeException 当统计过程中发生数据库异常或其他系统异常时抛出

     * 示例返回：
     * {
     *   "北京市": 25,
     *   "上海市": 18,
     *   "广东省": 12,
     *   "未知": 2
     * }
     */
    @Override
    public Map<String, Long> getProvinceStatsByAssetCategory(String assetCategory) {
        try {
            log.info("开始按资产分类统计网信资产省份分布 - assetCategory: {}", assetCategory);

            // 参数校验
            if (assetCategory == null || assetCategory.trim().isEmpty()) {
                log.warn("资产分类参数为空，无法进行统计");
                return Collections.emptyMap();
            }

            // 使用网信资产表自身的province字段进行统计
            List<Map<String, Object>> stats = cyberAssetMapper.selectProvinceStatsByAssetCategory(assetCategory);

            Map<String, Long> result = new HashMap<>();
            for (Map<String, Object> stat : stats) {
                String province = (String) stat.get("province");
                Long count = (Long) stat.get("count");

                // 处理省份为null或空字符串的情况，统一转为"未知"
                // 考虑因素：历史数据可能没有填写省份信息，或者数据录入时遗漏
                if (province == null || province.trim().isEmpty()) {
                    province = "未知";
                }
                result.put(province, count);
            }

            log.info("按资产分类统计网信资产省份分布完成 - assetCategory: {}, 统计省份数: {}",
                    assetCategory, result.size());
            return result;
        } catch (Exception e) {
            log.error("按资产分类统计网信资产省份分布失败 - assetCategory: {}", assetCategory, e);
            throw new RuntimeException("统计失败：" + e.getMessage());
        }
    }
}
