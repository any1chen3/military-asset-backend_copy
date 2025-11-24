package com.military.asset.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.military.asset.entity.SoftwareAsset;
import com.military.asset.mapper.SoftwareAssetMapper;
import com.military.asset.service.SoftwareAssetService;
import com.military.asset.utils.CategoryMapUtils;
import com.military.asset.utils.ProvinceAutoFillTool; // 新增：导入同步工具（仅用于上报单位同步）
import com.baomidou.mybatisplus.extension.plugins.pagination.Page; // 新增：导入Page类
import com.military.asset.vo.ExcelErrorVO;
import com.military.asset.vo.excel.SoftwareAssetExcelVO;
import com.military.asset.vo.stat.SoftwareAssetStatisticRow;
import com.military.asset.vo.stat.SoftwareAssetStatisticVO;
import com.military.asset.vo.ReportUnitImportanceVO;
import com.military.asset.vo.SoftwareUpgradeEvaluationRequest;
import com.military.asset.vo.SoftwareUpgradeRecommendationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import jakarta.annotation.Resource;// 新增：资源注入注解

import java.util.Objects;
import java.util.Arrays;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.LinkedHashMap;
import com.military.asset.utils.SoftwareUpgradeFormulaUtils;
import com.military.asset.utils.ReportUnitImportanceUtils;

//导出功能依赖
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
// 接口6(c)用
import org.springframework.beans.factory.annotation.Autowired;


/**
 * 软件资产业务层实现类
 * 实现SoftwareAssetService接口定义的所有业务逻辑，包含数据校验、数据库交互等
 * 继承MyBatis-Plus的ServiceImpl，自动获得baseMapper（无需手动注入）
 * - getExistingAssetsMap(): 实现完整资产对象Map的加载，用于导入时关键字段比较

 * 新增功能：
 * - 上报单位表同步：在增删改操作中同步上报单位表状态（软件资产表不需要省市字段）
 */
@Service
@Slf4j
@SuppressWarnings("unused")
public class SoftwareAssetServiceImpl extends ServiceImpl<SoftwareAssetMapper, SoftwareAsset> implements SoftwareAssetService {

    /**
     * 分类映射表：从工具类获取，存储分类编码与标准分类名称的对应关系
     */
    private final Map<String, String> CATEGORY_MAP = CategoryMapUtils.initSoftwareCategoryMap();

    /**
     * 合法服务状态列表：业务规则限定软件资产的服务状态只能是"在用"、"闲置"、"报废"、"封闭"
     */
    private final List<String> LEGAL_SERVICE_STATUS = List.of("在用", "闲置", "报废", "封闭");

    /**
//     * 最大有效年限：业务规则限定资产投入使用日期不能早于当前时间50年 (1115修改不需要了)
//     */
//    private static final int MAX_VALID_YEARS = 76;

    /**
     * 软件资产数据访问接口  用于接口6（c）
     * 用于执行软件资产表的数据库操作，包括自定义查询和统计
     */
    @Autowired
    private SoftwareAssetMapper softwareAssetMapper;

    // ============================ 新增依赖注入 ============================

    /**
     * 上报单位同步工具：仅用于同步上报单位表状态
     * 注意：软件资产表不需要省市字段，所以只使用syncReportUnit方法
     */
    @Resource
    private ProvinceAutoFillTool provinceAutoFillTool;

    // ============================ 新增方法实现 ============================

    @Override
    public Map<String, SoftwareAsset> getExistingAssetsMap() {
        try {
            // 查询所有已存在的软件资产（完整对象）
            List<SoftwareAsset> existingAssets = baseMapper.selectAllExistingAssets();

            // 转换为Map结构，键为资产ID，值为完整资产对象
            // 使用Collectors.toMap提供O(1)的查询性能
            Map<String, SoftwareAsset> assetsMap = existingAssets.stream()
                    .collect(Collectors.toMap(
                            SoftwareAsset::getId,  // 键：资产ID
                            asset -> asset,        // 值：完整资产对象
                            (existing, replacement) -> existing  // 冲突处理：保留现有值
                    ));

            log.info("成功加载{}条软件资产到内存Map，用于导入时关键字段比较", assetsMap.size());
            return assetsMap;

        } catch (Exception e) {
            log.error("加载软件资产Map失败，无法进行关键字段比较", e);
            throw new RuntimeException("加载资产数据失败: " + e.getMessage());
        }
    }
    @Override
    public List<SoftwareAssetStatisticVO> statisticsByReportUnit() {
        List<SoftwareAssetStatisticRow> rows = baseMapper.selectStatisticsByReportUnit();
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream()
                .map(this::buildStatisticVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SoftwareUpgradeRecommendationVO> generateUpgradeRecommendations(String reportUnit) {
        if (!StringUtils.hasText(reportUnit)) {
            throw new IllegalArgumentException("上报单位不能为空");
        }

        List<SoftwareAsset> assets = softwareAssetMapper.selectByReportUnitLight(reportUnit.trim());

        if (assets.isEmpty()) {
            throw new IllegalArgumentException("指定上报单位下未找到任何软件资产");
        }

        List<SoftwareUpgradeRecommendationVO> results = new ArrayList<>(assets.size());
        for (SoftwareAsset asset : assets) {
            SoftwareUpgradeEvaluationRequest derived = SoftwareUpgradeFormulaUtils.deriveEvaluationFromAsset(asset);

            BigDecimal necessity = SoftwareUpgradeFormulaUtils.calculateNecessity(
                    derived.getCoefficient(),
                    derived.getSecurityIndicator(),
                    derived.getPerformanceIndicator(),
                    derived.getRequirementMatch());

            String recommendation = SoftwareUpgradeFormulaUtils.buildRecommendation(asset.getAssetName(), necessity);

            SoftwareUpgradeRecommendationVO vo = new SoftwareUpgradeRecommendationVO();
            vo.setAssetId(asset.getId());
            vo.setAssetName(asset.getAssetName());
            vo.setReportUnit(asset.getReportUnit());
            vo.setNecessityScore(necessity);
            vo.setUpgradeRequired(SoftwareUpgradeFormulaUtils.needsUpgrade(necessity));
            vo.setRecommendation(recommendation);
            results.add(vo);
        }

        return results;
    }

    @Override
    public List<ReportUnitImportanceVO> analyzeReportUnitImportance(String reportUnit) {
        if (!StringUtils.hasText(reportUnit)) {
            throw new IllegalArgumentException("上报单位不能为空");
        }

        List<SoftwareAsset> assets = softwareAssetMapper.selectByReportUnitLight(reportUnit.trim());

        if (assets.isEmpty()) {
            throw new IllegalArgumentException("指定上报单位下未找到任何软件资产");
        }

        List<BigDecimal> scores = new ArrayList<>(assets.size());
        for (SoftwareAsset asset : assets) {
            scores.add(ReportUnitImportanceUtils.deriveScoreFromAsset(asset));
        }

        BigDecimal avgScore = ReportUnitImportanceUtils.averageScore(scores);
        String level = ReportUnitImportanceUtils.importanceLevel(avgScore);
        String advice = ReportUnitImportanceUtils.buildAdvice(reportUnit, avgScore, level, assets.size());

        ReportUnitImportanceVO vo = new ReportUnitImportanceVO();
        vo.setReportUnit(reportUnit);
        vo.setAssetCount(assets.size());
        vo.setImportanceScore(avgScore);
        vo.setImportanceLevel(level);
        vo.setAdvice(advice);

        return Collections.singletonList(vo);
    }
    // ============================ 原有方法实现（添加上报单位同步） ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveSoftwareAssets(List<SoftwareAssetExcelVO> validDataList) {
        // 调用原有的 batchSaveValidData 方法
        batchSaveValidData(validDataList);
    }

    @Override
    public List<String> getExistingIds() {
        try {
            List<String> ids = baseMapper.selectAllExistingIds();
            log.info("查询软件资产已存在ID完成，共{}条记录", ids.size());
            return ids;
        } catch (Exception e) {
            log.error("查询软件资产ID列表失败", e);
            throw new RuntimeException("查询资产ID失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveValidData(List<SoftwareAssetExcelVO> validVoList) {
        if (validVoList.isEmpty()) {
            log.info("无合法的软件资产数据需要保存");
            return;
        }

        List<SoftwareAsset> entities = new ArrayList<>();
        for (SoftwareAssetExcelVO vo : validVoList) {
            SoftwareAsset entity = new SoftwareAsset();
            BeanUtils.copyProperties(vo, entity);
            entity.setCreateTime(LocalDateTime.now());
            entities.add(entity);
        }

        baseMapper.insertBatch(entities);
        log.info("软件资产批量保存成功，共{}条记录", entities.size());

        // ============ 新增：上报单位表同步（批量导入场景） ============
        // 注意：软件资产表没有省市字段，所以省份参数传null
        // 遍历所有成功保存的实体，同步上报单位表状态
        for (SoftwareAsset entity : entities) {
            provinceAutoFillTool.syncReportUnit(
                    entity.getReportUnit(),  // 上报单位名称
                    null,                    // 软件资产无省份字段，传null
                    "software",              // 资产类型：软件
                    false                    // isDelete=false：新增场景
            );
        }
        log.info("软件资产批量导入完成，已同步上报单位表状态");
    }

    @Override
    public void handleImportResult(int totalRow, int validRow, List<ExcelErrorVO> errorList) {
        log.info("==== 软件资产Excel导入结果统计 ====");
        log.info("总记录数：{} | 成功导入：{} | 导入失败：{}", totalRow, validRow, errorList.size());

        if (!errorList.isEmpty()) {
            log.warn("导入错误详情：");
            errorList.forEach(error ->
                    log.warn("行号：{} | 错误字段：{} | 错误原因：{}",
                            error.getExcelRowNum(), error.getErrorFields(), error.getErrorMsg())
            );
        }
    }

    @Override
    public SoftwareAsset getById(String id) {
        // 移除32位长度限制，只检查非空和格式
        if (!StringUtils.hasText(id) || !isValidAssetId(id)) {
            throw new RuntimeException("资产ID格式错误，必须由字母和数字组成");
        }

        SoftwareAsset asset = baseMapper.selectById(id);
        if (asset == null) {
            throw new RuntimeException("未找到ID为" + id + "的软件资产");
        }

        log.info("查询软件资产详情成功，ID：{}", id);
        return asset;
    }

    // ====================== 修改：软件资产联合查询方法实现（支持实有数量范围查询 + 盘点单位筛选） ======================
    @Override
    public Object combinedQuery(Integer pageNum, Integer pageSize,
                                String reportUnit, String categoryCode, String assetCategory,
                                String acquisitionMethod, String deploymentScope, String deploymentForm,
                                String bearingNetwork, Integer quantityMin, Integer quantityMax,
                                String serviceStatus, String startUseDateStart, String startUseDateEnd,
                                String inventoryUnit) {
        try {
            log.info("执行软件资产联合查询：pageNum={}, pageSize={}, reportUnit={}, categoryCode={}, assetCategory={}, " +
                            "acquisitionMethod={}, deploymentScope={}, deploymentForm={}, bearingNetwork={}, quantityMin={}, quantityMax={}, " +
                            "serviceStatus={}, startUseDateStart={}, startUseDateEnd={}, inventoryUnit={}",
                    pageNum, pageSize, reportUnit, categoryCode, assetCategory, acquisitionMethod,
                    deploymentScope, deploymentForm, bearingNetwork, quantityMin, quantityMax,
                    serviceStatus, startUseDateStart, startUseDateEnd, inventoryUnit);

            // 创建分页对象，使用MyBatis-Plus的分页功能
            Page<SoftwareAsset> page = new Page<>(pageNum, pageSize);

            // 调用Mapper进行联合查询
            Page<SoftwareAsset> resultPage = baseMapper.combinedQuery(
                    page, reportUnit, categoryCode, assetCategory,
                    acquisitionMethod, deploymentScope, deploymentForm, bearingNetwork,
                    quantityMin, quantityMax, serviceStatus, startUseDateStart, startUseDateEnd,
                    inventoryUnit
            );

            log.info("软件资产联合查询完成，共查询到{}条记录，分{}页显示",
                    resultPage.getTotal(), resultPage.getPages());
            return resultPage;

        } catch (Exception e) {
            log.error("软件资产联合查询执行失败", e);
            throw new RuntimeException("联合查询执行失败: " + e.getMessage());
        }
    }

    /**
     * 新增软件应用资产（集成上报单位表同步）
     * 功能概述：
     * 本方法用于新增单条软件资产记录，包含完整的数据校验、业务处理、数据保存和上报单位表同步功能。
     * 软件资产表与其他资产表的主要区别：没有省市字段，所有省市信息通过上报单位表间接管理。
     * 核心流程：
     *  1. 自动填充省市阶段 → 2. 数据校验阶段 → 3. 数据处理阶段 → 4. 数据保存阶段 → 5. 上报单位表同步阶段
     * 数据校验规则（按字段顺序）：
     * 1.1 主键：必填，数字字母组合，确保唯一性
     * 1.2 上报单位：必填字段
     * 1.3 分类编码与资产分类：必填，严格匹配预设映射关系
     * 1.4 资产名称：必填字段
     * 1.5 取得方式：必填，固定选项（购置/自主开发/合作开发/其他）
     * 1.6 部署范围：必填，固定选项（军以下/全军/战区/军级单位内部/军种）
     * 1.7 部署形式：可选字段，但如果有值则不能为空字符串
     * 1.8 承载网络：可选字段，但如果有值则不能为空字符串
     * 1.9 实有数量：必填，非负整数（支持0）
     * 1.10 计量单位：必填字段
     * 1.11 单价：可选字段，如果填写则必须为非负数
     * 1.12 服务状态：必填，固定选项（在用/闲置/报废/封闭）
     * 1.13 投入使用日期：必填，必须≥1949-10-01且≤当前日期
     * 1.14 盘点单位：必填字段

     * 特殊处理逻辑：
     * - 金额字段：如果金额为空，且单价和实有数量都存在，则自动计算金额（单价×数量）
     * - 创建时间：系统自动生成当前时间
     * - 上报单位同步：自动推导省市信息并同步到上报单位表
     * 事务管理：
     * - 使用@Transactional注解确保操作原子性
     * - 任何校验失败或保存失败都会回滚整个事务
     * 适用场景：
     * - 前端手动新增软件资产
     * - 需要完整校验和上报单位同步的业务场景
     * - 单条记录新增操作
     * 注意事项：
     * - 软件资产表没有省市字段，所有省市信息通过上报单位名称自动推导
     * - 分类编码与资产分类必须严格匹配预设映射，否则校验失败
     * - 投入使用日期有严格的时间范围限制（1949年至今）
     * - 金额计算尊重用户输入，仅在金额为空时自动计算
     * @param asset 软件资产实体对象，包含所有必填和可选字段
     * @throws RuntimeException 当任何校验失败或保存失败时抛出，包含具体的错误信息
     * 日志记录：
     * - 记录详细的校验过程和结果
     * - 记录数据保存和同步状态
     * - 便于问题排查和系统监控
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(SoftwareAsset asset) {
        log.info("开始新增软件资产，ID：{}", asset.getId());

        // ==================== 1. 数据校验阶段 ====================

        // 1.1 主键校验：必填，数字字母组合，确保唯一
        validatePrimaryKey(asset);

        // 1.2 上报单位校验：必填
        validateReportUnit(asset);

        // 1.3 分类编码与资产分类校验：必填，严格匹配
        validateCategory(asset);

        // 1.4 资产名称校验：必填
        validateAssetName(asset);

        // 1.5 取得方式校验：必填，固定选项
        validateAcquisitionMethod(asset);

        // 1.6 部署范围校验：必填，固定选项
        validateDeploymentScope(asset);

        // 1.7 部署形式校验：可选，但如果有值则不能为空
        validateDeploymentForm(asset);

        // 1.8 承载网络校验：可选，但如果有值则不能为空
        validateBearingNetwork(asset);

        // 1.9 实有数量校验：必填，非负整数
        validateActualQuantity(asset);

        // 1.10 计量单位校验：必填
        validateUnit(asset);

        // 1.11 单价校验：可选，如果填写则必须非负
        validateUnitPrice(asset);

        // 1.12 服务状态校验：必填，固定选项
        validateServiceStatus(asset);

        // 1.13 投入使用日期校验：必填，≥1949-10-01且≤当前日期
        validatePutIntoUseDate(asset);

        // 1.14 盘点单位校验：必填
        validateInventoryUnit(asset);

        // ==================== 2. 数据处理阶段 ====================

        // 2.1 系统自动生成创建时间
        asset.setCreateTime(LocalDateTime.now());

        // 2.2 计算金额（如果金额为空，且有单价和数量，则自动计算）
        calculateAmount(asset);

        // ==================== 3. 数据保存阶段 ====================

        baseMapper.insert(asset);
        log.info("新增软件资产成功，ID：{}，资产名称：{}", asset.getId(), asset.getAssetName());

        // ==================== 4. 上报单位表同步阶段 ====================

        // 4.1 上报单位表同步（单条新增场景）
        provinceAutoFillTool.syncReportUnit(
                asset.getReportUnit(),  // 上报单位名称
                null,                   // 软件资产无省份字段，传null
                "software",             // 资产类型：软件
                false                   // isDelete=false：新增场景
        );
        log.debug("软件资产新增完成，已同步上报单位表状态");
    }

// ==================== 详细的校验方法 ====================

    /**
     * 1.1 主键校验
     * 规则：必填，唯一标识，数字字母组合，确保在组内唯一且不与之前组别冲突
     */
    private void validatePrimaryKey(SoftwareAsset asset) {
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
     * 1.2 上报单位校验
     * 规则：必填
     */
    private void validateReportUnit(SoftwareAsset asset) {
        if (!StringUtils.hasText(asset.getReportUnit())) {
            throw new RuntimeException("上报单位不能为空");
        }
        log.debug("上报单位：{}", asset.getReportUnit());
    }

    /**
     * 1.3 分类编码与资产分类校验
     * 规则：必填，与资产分类严格匹配
     */
    private void validateCategory(SoftwareAsset asset) {
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
     * 1.4 资产名称校验
     * 规则：必填
     */
    private void validateAssetName(SoftwareAsset asset) {
        if (!StringUtils.hasText(asset.getAssetName())) {
            throw new RuntimeException("资产名称不能为空");
        }
        log.debug("资产名称：{}", asset.getAssetName());
    }

    /**
     * 1.5 取得方式校验
     * 规则：必填，固定选项：购置、自主开发、合作开发、其他
     */
    private void validateAcquisitionMethod(SoftwareAsset asset) {
        if (!StringUtils.hasText(asset.getAcquisitionMethod())) {
            throw new RuntimeException("取得方式不能为空");
        }

        List<String> acquisitionMethods = Arrays.asList("购置", "自主开发", "合作开发", "其他");
        if (!acquisitionMethods.contains(asset.getAcquisitionMethod())) {
            throw new RuntimeException("无效的取得方式：" + asset.getAcquisitionMethod() +
                    "，允许值：" + String.join("、", acquisitionMethods));
        }

        log.debug("取得方式：{}", asset.getAcquisitionMethod());
    }

    /**
     * 1.6 部署范围校验
     * 规则：必填，固定选项：军以下、全军、战区、军级单位内部、军种
     */
    private void validateDeploymentScope(SoftwareAsset asset) {
        if (!StringUtils.hasText(asset.getDeploymentScope())) {
            throw new RuntimeException("部署范围不能为空");
        }

        List<String> deploymentScopes = Arrays.asList("军以下", "全军", "战区", "军级单位内部", "军种");
        if (!deploymentScopes.contains(asset.getDeploymentScope())) {
            throw new RuntimeException("无效的部署范围：" + asset.getDeploymentScope() +
                    "，允许值：" + String.join("、", deploymentScopes));
        }

        log.debug("部署范围：{}", asset.getDeploymentScope());
    }

    /**
     * 1.7 部署形式校验
     * 规则：可选，但如果有值则不能为空
     */
    private void validateDeploymentForm(SoftwareAsset asset) {
        // 可选字段，但如果有值则不能为空
        if (asset.getDeploymentForm() != null && asset.getDeploymentForm().trim().isEmpty()) {
            throw new RuntimeException("部署形式不能为空字符串");
        }

        if (StringUtils.hasText(asset.getDeploymentForm())) {
            log.debug("部署形式：{}", asset.getDeploymentForm());
        }
    }

    /**
     * 1.8 承载网络校验
     * 规则：可选，但如果有值则不能为空
     */
    private void validateBearingNetwork(SoftwareAsset asset) {
        // 可选字段，但如果有值则不能为空
        if (asset.getBearingNetwork() != null && asset.getBearingNetwork().trim().isEmpty()) {
            throw new RuntimeException("承载网络不能为空字符串");
        }

        if (StringUtils.hasText(asset.getBearingNetwork())) {
            log.debug("承载网络：{}", asset.getBearingNetwork());
        }
    }

    /**
     * 1.9 实有数量校验
     * 规则：必填，非负整数
     */
    private void validateActualQuantity(SoftwareAsset asset) {
        if (asset.getActualQuantity() == null) {
            throw new RuntimeException("实有数量不能为空");
        }

        if (asset.getActualQuantity() < 0) {
            throw new RuntimeException("实有数量必须为非负整数");
        }

        log.debug("实有数量：{}", asset.getActualQuantity());
    }

    /**
     * 1.10 计量单位校验
     * 规则：必填
     */
    private void validateUnit(SoftwareAsset asset) {
        if (!StringUtils.hasText(asset.getUnit())) {
            throw new RuntimeException("计量单位不能为空");
        }
        log.debug("计量单位：{}", asset.getUnit());
    }

    /**
     * 1.11 单价校验
     * 规则：可选，如果填写则必须非负
     */
    private void validateUnitPrice(SoftwareAsset asset) {
        // 可选字段，如果有值则校验非负
        if (asset.getUnitPrice() != null) {
            if (asset.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("单价不能为负数");
            }
            log.debug("单价：{}", asset.getUnitPrice());
        }
    }

    /**
     * 1.12 服务状态校验
     * 规则：必填，固定选项：在用、闲置、报废、封闭
     */
    private void validateServiceStatus(SoftwareAsset asset) {
        if (!StringUtils.hasText(asset.getServiceStatus())) {
            throw new RuntimeException("服务状态不能为空");
        }

        List<String> serviceStatuses = Arrays.asList("在用", "闲置", "报废", "封闭");
        if (!serviceStatuses.contains(asset.getServiceStatus())) {
            throw new RuntimeException("无效的服务状态：" + asset.getServiceStatus() +
                    "，允许值：" + String.join("、", serviceStatuses));
        }

        log.debug("服务状态：{}", asset.getServiceStatus());
    }

    /**
     * 1.13 投入使用日期校验
     * 规则：必填，≥1949-10-01且≤当前日期
     */
    private void validatePutIntoUseDate(SoftwareAsset asset) {
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
     * 1.14 盘点单位校验
     * 规则：必填
     */
    private void validateInventoryUnit(SoftwareAsset asset) {
        if (!StringUtils.hasText(asset.getInventoryUnit())) {
            throw new RuntimeException("盘点单位不能为空");
        }
        log.debug("盘点单位：{}", asset.getInventoryUnit());
    }

// ==================== 金额计算方法 ====================

    /**
     * 金额计算
     * 规则：可选，数量×单价
     * 逻辑：如果金额为空，且有单价和数量，则自动计算
     * 如果金额已有值，则不自动计算（尊重用户输入）
     */
    private void calculateAmount(SoftwareAsset asset) {
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

    /**
     * 修改软件应用资产（集成上报单位表同步 + 更新创建时间）
     * 功能概述：
     * 本方法用于修改单条软件资产记录，包含数据校验、业务处理、数据更新和上报单位表同步功能。
     * 核心特点：修改成功后，将创建时间更新为当前时间，作为最后修改时间的参考。

     * 核心流程：
     * 1. 数据存在性校验阶段 → 2. 数据校验阶段 → 3. 数据处理阶段 → 4. 数据更新阶段 → 5. 上报单位表同步阶段

     * 数据校验规则（与新增一致）：
     * 2.1 上报单位校验：必填
     * 2.2 分类编码与资产分类校验：必填，严格匹配
     * 2.3 资产名称校验：必填
     * 2.4 取得方式校验：必填，固定选项
     * 2.5 部署范围校验：必填，固定选项
     * 2.6 部署形式校验：可选，但如果有值则不能为空
     * 2.7 承载网络校验：可选，但如果有值则不能为空
     * 2.8 实有数量校验：必填，非负整数
     * 2.9 计量单位校验：必填
     * 2.10 单价校验：可选，如果填写则必须非负
     * 2.11 服务状态校验：必填，固定选项
     * 2.12 投入使用日期校验：必填，≥1949-10-01且≤当前日期
     * 2.13 盘点单位校验：必填

     * 特殊处理逻辑：
     * - 创建时间更新：修改成功后，将创建时间更新为当前时间，作为最后修改时间的参考
     * - 主键ID：不允许修改，使用原记录ID
     * - 金额计算：如果金额为空，且有单价和数量，则自动重新计算
     * - 上报单位同步：如果上报单位发生变更，需要同步新旧两个单位的状态

     * 事务管理：
     * - 使用@Transactional注解确保操作原子性
     * - 任何校验失败或更新失败都会回滚整个事务

     * 适用场景：
     * - 前端手动修改软件资产信息
     * - 需要完整数据校验和上报单位同步的业务场景
     * - 单条记录更新操作

     * 注意事项：
     * - 修改操作必须基于已存在的记录
     * - 主键ID是唯一标识，不允许修改
     * - 上报单位变更会影响上报单位表的状态同步
     * - 创建时间在修改成功后会被更新，反映最后修改时间
     *
     * @param asset 软件资产对象（包含修改后的数据）
     * @throws RuntimeException 当资产不存在、数据校验失败或更新失败时抛出业务异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SoftwareAsset asset) {
        log.info("🔄 开始修改软件资产，ID：{}", asset.getId());

        // ==================== 1. 数据存在性校验阶段 ====================

        // 1.1 校验主键ID必填
        if (!StringUtils.hasText(asset.getId())) {
            throw new RuntimeException("修改软件资产失败：主键ID不能为空");
        }

        // 1.2 查询原记录，确保数据存在
        SoftwareAsset existingAsset = baseMapper.selectById(asset.getId());
        if (existingAsset == null) {
            throw new RuntimeException("修改软件资产失败：资产不存在，ID：" + asset.getId());
        }

        // 1.3 保存原上报单位信息，用于后续同步比较
        String originalReportUnit = existingAsset.getReportUnit();
        String newReportUnit = asset.getReportUnit();
        boolean reportUnitChanged = !Objects.equals(originalReportUnit, newReportUnit);

        log.debug("📋 找到原软件资产记录 - ID: {}, 原上报单位: {}, 新上报单位: {}, 单位变更: {}",
                asset.getId(), originalReportUnit, newReportUnit, reportUnitChanged);

        // ==================== 2. 数据校验阶段（与新增一致） ====================

        // 2.1 上报单位校验：必填
        validateReportUnit(asset);

        // 2.2 分类编码与资产分类校验：必填，严格匹配
        validateCategory(asset);

        // 2.3 资产名称校验：必填
        validateAssetName(asset);

        // 2.4 取得方式校验：必填，固定选项
        validateAcquisitionMethod(asset);

        // 2.5 部署范围校验：必填，固定选项
        validateDeploymentScope(asset);

        // 2.6 部署形式校验：可选，但如果有值则不能为空
        validateDeploymentForm(asset);

        // 2.7 承载网络校验：可选，但如果有值则不能为空
        validateBearingNetwork(asset);

        // 2.8 实有数量校验：必填，非负整数
        validateActualQuantity(asset);

        // 2.9 计量单位校验：必填
        validateUnit(asset);

        // 2.10 单价校验：可选，如果填写则必须非负
        validateUnitPrice(asset);

        // 2.11 服务状态校验：必填，固定选项
        validateServiceStatus(asset);

        // 2.12 投入使用日期校验：必填，≥1949-10-01且≤当前日期
        validatePutIntoUseDate(asset);

        // 2.13 盘点单位校验：必填
        validateInventoryUnit(asset);

        log.debug("✅ 软件资产数据校验通过，ID：{}", asset.getId());

        // ==================== 3. 数据处理阶段 ====================

        // 3.1 重新计算金额（如果金额为空，且有单价和数量，则自动计算）
        calculateAmount(asset);

        // 3.2 创建时间将在数据更新成功后设置为当前时间（见第4步）

        log.debug("🛠️ 数据处理完成，准备更新数据");

        // ==================== 4. 数据更新阶段 ====================

        // 4.1 在更新前设置创建时间为当前时间（作为最后修改时间）
        asset.setCreateTime(LocalDateTime.now());

        // 4.2 执行更新操作
        int updateCount = baseMapper.updateById(asset);
        if (updateCount == 0) {
            throw new RuntimeException("修改软件资产失败，ID：" + asset.getId());
        }

        log.info("✅ 修改软件资产成功，ID：{}，资产名称：{}，创建时间已更新",
                asset.getId(), asset.getAssetName());

        // ==================== 5. 上报单位表同步阶段 ====================

        // 5.1 同步上报单位表状态
        // 情况1：上报单位未变更，只同步当前单位
        if (!reportUnitChanged) {
            provinceAutoFillTool.syncReportUnit(
                    newReportUnit,      // 上报单位名称
                    null,               // 软件资产无省份字段
                    "software",         // 资产类型：软件
                    false               // isDelete=false：更新场景
            );
            log.debug("🔄 软件资产修改完成（单位未变更），已同步上报单位表状态 - 单位: {}", newReportUnit);
        }
        // 情况2：上报单位发生变更，需要同步新旧两个单位
        else {
            // 同步原单位（可能不再有软件资产）
            provinceAutoFillTool.syncReportUnit(
                    originalReportUnit, // 原上报单位名称
                    null,               // 软件资产无省份字段
                    "software",         // 资产类型：软件
                    true                // isDelete=true：原单位可能不再有此资产
            );

            // 同步新单位（新增软件资产）
            provinceAutoFillTool.syncReportUnit(
                    newReportUnit,      // 新上报单位名称
                    null,               // 软件资产无省份字段
                    "software",         // 资产类型：软件
                    false               // isDelete=false：新单位有此资产
            );

            log.debug("🔄 软件资产修改完成（单位已变更），已同步新旧单位状态 - 原单位: {}, 新单位: {}",
                    originalReportUnit, newReportUnit);
        }
    }

    /**
     * 删除软件应用资产（集成上报单位表同步）
     * 功能概述：
     * 本方法用于删除单条软件资产记录，包含资产存在性校验、数据删除和上报单位表同步功能。
     * 软件资产表与其他资产表的主要区别：没有省市字段，所有省市信息通过上报单位表间接管理。

     * 核心流程：
     * 1. 资产存在性校验阶段 → 2. 数据删除阶段 → 3. 上报单位表同步阶段

     * 业务规则：
     * - 必须先查询资产是否存在，确保操作的合法性
     * - 删除操作必须同步更新上报单位表的状态标志
     * - 使用事务确保数据一致性，任何步骤失败都会回滚

     * 同步逻辑：
     * - 调用 provinceAutoFillTool.syncReportUnit 方法
     * - 设置 isDelete=true，表示删除场景
     * - 如果该单位不再有软件资产，系统会自动将软件资产状态标志设为0
     * - 软件资产表没有省市字段，省份参数传递null

     * 事务管理：
     * - 使用@Transactional注解确保操作原子性
     * - 任何校验失败或删除失败都会回滚整个事务
     * - rollbackFor = Exception.class 确保所有异常都会触发回滚

     * 适用场景：
     * - 前端手动删除软件资产
     * - 需要完整事务管理和上报单位同步的业务场景
     * - 单条记录删除操作

     * 注意事项：
     * - 删除前必须查询资产信息，获取上报单位名称用于同步
     * - 删除后需要同步上报单位表，确保状态标志准确
     * - 如果资产不存在，抛出明确的业务异常信息
     * - 日志记录要详细，便于问题排查和审计追踪

     * @param id 软件资产主键ID，必填参数
     * @throws RuntimeException 当资产不存在或删除失败时抛出业务异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(String id) {
        log.info("🚀 开始删除软件资产，ID：{}", id);

        // ==================== 1. 资产存在性校验阶段 ====================

        // 1.1 根据ID查询资产信息
        SoftwareAsset asset = baseMapper.selectById(id);
        if (asset == null) {
            log.error("❌ 软件资产不存在，删除失败，ID：{}", id);
            throw new RuntimeException("软件资产不存在，ID：" + id);
        }

        // 1.2 获取上报单位信息，用于后续同步操作
        String reportUnit = asset.getReportUnit();
        log.debug("📋 找到待删除软件资产 - ID: {}, 上报单位: {}, 资产名称: {}",
                id, reportUnit, asset.getAssetName());

        // ==================== 2. 数据删除阶段 ====================

        // 2.1 执行物理删除操作
        int deleteCount = baseMapper.deleteById(id);
        if (deleteCount == 0) {
            log.error("❌ 软件资产删除失败，可能已被其他操作删除，ID：{}", id);
            throw new RuntimeException("删除软件资产失败，ID：" + id);
        }

        log.info("✅ 删除软件资产成功，ID：{}，资产名称：{}", id, asset.getAssetName());

        // ==================== 3. 上报单位表同步阶段 ====================

        // 3.1 同步上报单位表状态（删除场景）
        // 作用：更新上报单位表中该单位的软件资产状态标志
        // 逻辑：如果该单位不再有软件资产，系统会自动将software_asset_status设为0
        provinceAutoFillTool.syncReportUnit(
                reportUnit,           // 上报单位名称（从已删除资产获取）
                null,                 // 软件资产无省份字段，传null
                "software",           // 资产类型：软件资产
                true                  // isDelete=true：删除场景，触发状态标志更新
        );
        log.debug("🔄 软件资产删除完成，已同步上报单位表状态 - 单位: {}", reportUnit);
    }

    @Override
    public boolean checkCategoryMatch(String categoryCode, String assetCategory) {
        String legalCategory = CATEGORY_MAP.get(categoryCode);
        if (legalCategory == null) {
            return false;
        }
        return legalCategory.trim().equals(assetCategory.trim());
    }

    // ============================ 私有工具方法 ============================

    private SoftwareAssetStatisticVO buildStatisticVO(SoftwareAssetStatisticRow row) {
        SoftwareAssetStatisticVO vo = new SoftwareAssetStatisticVO();
        vo.setReportUnit(row.getReportUnit());
        int total = safeValue(row.getTotalQuantity());
        vo.setTotalQuantity(total);

        SoftwareAssetStatisticVO.AcquisitionStatistic acquisition = new SoftwareAssetStatisticVO.AcquisitionStatistic();
        acquisition.setPurchase(buildStatisticItem(row.getPurchaseQuantity(), total));
        acquisition.setSelfDeveloped(buildStatisticItem(row.getSelfDevelopedQuantity(), total));
        acquisition.setCoDeveloped(buildStatisticItem(row.getCoDevelopedQuantity(), total));
        acquisition.setOther(buildStatisticItem(row.getOtherQuantity(), total));
        vo.setAcquisition(acquisition);

        SoftwareAssetStatisticVO.ServiceStatusStatistic serviceStatus = new SoftwareAssetStatisticVO.ServiceStatusStatistic();
        serviceStatus.setInUse(buildStatisticItem(row.getInUseQuantity(), total));
        serviceStatus.setIdle(buildStatisticItem(row.getIdleQuantity(), total));
        serviceStatus.setScrapped(buildStatisticItem(row.getScrappedQuantity(), total));
        serviceStatus.setClosed(buildStatisticItem(row.getClosedQuantity(), total));
        vo.setServiceStatus(serviceStatus);

        return vo;
    }

    private SoftwareAssetStatisticVO.StatisticItem buildStatisticItem(Integer quantity, int total) {
        SoftwareAssetStatisticVO.StatisticItem item = new SoftwareAssetStatisticVO.StatisticItem();
        int safeQuantity = safeValue(quantity);
        item.setQuantity(safeQuantity);
        item.setPercent(calculatePercent(safeQuantity, total));
        return item;
    }

    private int safeValue(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal calculatePercent(int quantity, int total) {
        if (total <= 0 || quantity <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(quantity)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

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
    public void addSoftwareAsset(SoftwareAsset asset) {
        // 直接调用原有的 add 方法，因为 add 方法已经集成了上报单位同步
        add(asset);
        log.debug("通过 addSoftwareAsset 方法新增软件资产成功，ID：{}", asset.getId());
    }

    @Override
    public void updateSoftwareAsset(SoftwareAsset asset) {
        // 直接调用原有的 update 方法，因为 update 方法已经集成了上报单位同步
        update(asset);
        log.debug("通过 updateSoftwareAsset 方法修改软件资产成功，ID：{}", asset.getId());
    }

    @Override
    public void deleteSoftwareAsset(String id) {
        // 直接调用原有的 remove 方法，因为 remove 方法已经集成了上报单位同步
        remove(id);
        log.debug("通过 deleteSoftwareAsset 方法删除软件资产成功，ID：{}", id);
    }

    // ============================ 新增额外接口 ============================
    /**
     * 接口1
     * 统计软件资产数量
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
    public Page<SoftwareAsset> queryByCategory(Page<SoftwareAsset> page, String categoryCode, String assetCategory) {
        return this.getBaseMapper().queryByCategory(page, categoryCode, assetCategory);
    }

    /**
     * 接口3
     * 实现按上报单位查询软件资产
     * 调用Mapper层的queryByReportUnit方法执行SQL查询
     */
    @Override
    public Page<SoftwareAsset> queryByReportUnit(Page<SoftwareAsset> page, String reportUnit) {
        return this.getBaseMapper().queryByReportUnit(page, reportUnit);
    }

// ==================== 新增：接口4相关方法实现 ====================

    @Override
    public List<Map<String, Object>> getProvinceUnitStats() {
        /**
         * 实现软件资产表省份单位统计（新逻辑：关联report_unit表）

         * 原问题：software_asset表没有province列，直接查询会报错
         * 新解决方案：通过关联report_unit表获取省份信息

         * SQL执行逻辑：
         *   SELECT ru.province, COUNT(DISTINCT sa.report_unit) as count
         *   FROM software_asset sa
         *   INNER JOIN report_unit ru ON sa.report_unit = ru.report_unit
         *   WHERE ru.province IS NOT NULL AND ru.province != ''
         *   GROUP BY ru.province
         *   ORDER BY count DESC

         * 优势：
         * - 解决了表结构限制问题
         * - 统一了省份信息来源
         * - 确保数据准确性和一致性
         */
        return this.getBaseMapper().selectProvinceUnitStats();
    }

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
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearSoftwareTableAndResetStatus() {
        log.info("🗑️ 开始清空软件资产表并重置上报单位表状态...");

        try {
            // 1. 清空software_asset表的所有数据
            int deletedCount = baseMapper.delete(null); // 删除所有记录
            log.info("✅ 清空软件资产表完成，共删除{}条记录", deletedCount);

            // 2. 重置report_unit表中软件资产状态为0
            int updatedCount = baseMapper.resetSoftwareAssetStatus();
            log.info("✅ 重置上报单位表软件资产状态完成，共更新{}条记录", updatedCount);

            log.info("🎉 软件资产表和状态重置完成");
        } catch (Exception e) {
            log.error("❌ 清空软件资产表失败: {}", e.getMessage(), e);
            throw new RuntimeException("清空软件资产表失败: " + e.getMessage());
        }
    }

    /**
     * 批量保存软件资产并同步省市信息（导入专用）
     * 🎯 与普通批量保存的区别：
     * 1. 软件资产表没有省市字段，但需要推导省市信息用于上报单位表
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
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveForImport(List<SoftwareAsset> assets) {
        if (assets == null || assets.isEmpty()) {
            log.info("ℹ️ 批量保存软件资产：无数据需要保存");
            return;
        }

        log.info("💾 开始批量保存软件资产并同步省市信息，共{}条数据", assets.size());

        try {
            // 1. 软件资产需要调用自动填充推导省市信息（虽然表没有省市字段，但上报单位表需要）
            for (SoftwareAsset asset : assets) {
                provinceAutoFillTool.fillAssetProvinceCity(asset, false);
            }

            // 2. 批量保存到software_asset表
            boolean saveResult = saveBatch(assets);
            if (!saveResult) {
                throw new RuntimeException("批量保存软件资产失败");
            }
            log.info("✅ 批量保存软件资产成功，共{}条", assets.size());

            // 3. 按上报单位分组，用于批量同步
            Map<String, List<SoftwareAsset>> unitGroupedAssets = assets.stream()
                    .collect(Collectors.groupingBy(SoftwareAsset::getReportUnit));

            log.info("📊 按单位分组完成，共{}个不同单位", unitGroupedAssets.size());

            // 4. 批量同步上报单位表
            List<ProvinceAutoFillTool.UnitSyncRequest> syncRequests = new ArrayList<>();
            for (Map.Entry<String, List<SoftwareAsset>> entry : unitGroupedAssets.entrySet()) {
                String unitName = entry.getKey();
                SoftwareAsset firstAsset = entry.getValue().get(0);
                syncRequests.add(new ProvinceAutoFillTool.UnitSyncRequest(
                        unitName,
                        firstAsset.getProvince(),  // 使用自动填充推导出的省份
                        "software",
                        false
                ));
            }

            // 执行批量同步
            provinceAutoFillTool.batchSyncReportUnits(syncRequests);

            log.info("✅ 软件资产批量导入完成，省市信息同步完成，涉及{}个单位", unitGroupedAssets.size());

        } catch (Exception e) {
            log.error("❌ 批量保存软件资产失败: {}", e.getMessage(), e);
            throw new RuntimeException("批量保存软件资产失败: " + e.getMessage());
        }
    }

    /**
     * 软件资产联合查询方法实现
     * 作用：根据动态条件分页查询软件资产数据
     * 特点：支持所有条件的动态组合查询，条件为null时忽略该条件
     *
     * @param pageInfo 分页信息对象，包含页码和页面大小
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
     * @param startUseDateStart 投入使用日期开始
     * @param startUseDateEnd 投入使用日期结束
     * @param inventoryUnit 盘点单位
     *
     * @return Page<SoftwareAsset> 分页查询结果
     */
    /**
     * 软件资产联合查询方法实现
     * 作用：根据动态条件分页查询软件资产数据
     * 特点：支持所有条件的动态组合查询，条件为null时忽略该条件
     * 注意：使用Java原生字符串判断，避免额外依赖
     */
    @Override
    public Page<SoftwareAsset> combinedQuery(Page<SoftwareAsset> pageInfo,
                                             String reportUnit, String categoryCode, String assetCategory,
                                             String acquisitionMethod, String deploymentScope, String deploymentForm,
                                             String bearingNetwork, Integer quantityMin, Integer quantityMax,
                                             String serviceStatus, String startUseDateStart, String startUseDateEnd,
                                             String inventoryUnit) {
        try {
            log.info("执行软件资产联合查询 - 条件: reportUnit={}, categoryCode={}, assetCategory={}",
                    reportUnit, categoryCode, assetCategory);

            // 构建查询条件
            QueryWrapper<SoftwareAsset> queryWrapper = new QueryWrapper<>();

            // 动态添加查询条件 - 使用Java原生字符串判断
            if (reportUnit != null && !reportUnit.trim().isEmpty()) {
                queryWrapper.like("report_unit", reportUnit);
            }
            if (categoryCode != null && !categoryCode.trim().isEmpty()) {
                queryWrapper.like("category_code", categoryCode);
            }
            if (assetCategory != null && !assetCategory.trim().isEmpty()) {
                queryWrapper.like("asset_category", assetCategory);
            }
            if (acquisitionMethod != null && !acquisitionMethod.trim().isEmpty()) {
                queryWrapper.eq("acquisition_method", acquisitionMethod);
            }
            if (deploymentScope != null && !deploymentScope.trim().isEmpty()) {
                queryWrapper.eq("deployment_scope", deploymentScope);
            }
            if (deploymentForm != null && !deploymentForm.trim().isEmpty()) {
                queryWrapper.eq("deployment_form", deploymentForm);
            }
            if (bearingNetwork != null && !bearingNetwork.trim().isEmpty()) {
                queryWrapper.eq("bearing_network", bearingNetwork);
            }
            if (quantityMin != null) {
                queryWrapper.ge("actual_quantity", quantityMin);
            }
            if (quantityMax != null) {
                queryWrapper.le("actual_quantity", quantityMax);
            }
            if (serviceStatus != null && !serviceStatus.trim().isEmpty()) {
                queryWrapper.eq("service_status", serviceStatus);
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
            Page<SoftwareAsset> result = baseMapper.selectPage(pageInfo, queryWrapper);
            log.info("软件资产联合查询完成，共{}条数据", result.getRecords().size());

            return result;

        } catch (Exception e) {
            log.error("软件资产联合查询失败", e);
            throw new RuntimeException("查询失败: " + e.getMessage());
        }
    }

    // ============================== 新增：各省份资产统计方法 ==============================
    /**
     * 获取各省份软件资产统计概览
     * 作用：统计34个省份+"未知"的软件资产数量和占比

     * 核心逻辑：
     * 1. 通过关联report_unit表获取软件资产的省份信息
     * 2. 统计每个省份的软件资产数量
     * 3. 计算每个省份软件资产占总量的百分比
     * 4. 包含"未知"省份的统计（无法推导出省份的单位）

     * 技术实现：
     * - 使用LEFT JOIN关联software_asset和report_unit表
     * - 使用COALESCE处理null值，将null省份转为"未知"
     * - 在数据库层面完成分组统计，提高性能
     * @return 包含总数量和各省份统计的结果
     */
    @Override
    public Map<String, Object> getProvinceAssetOverview() {
        log.info("开始统计各省份软件资产数量和占比...");

        Map<String, Object> result = new HashMap<>();

        // 1. 获取软件资产总数
        long totalSoftwareCount = baseMapper.selectCount(null);
        result.put("totalSoftwareCount", totalSoftwareCount);

        // 2. 获取各省份软件资产统计（通过关联report_unit表）
        List<Map<String, Object>> provinceStats = baseMapper.selectProvinceSoftwareStats();

        // 3. 转换为前端需要的格式并计算百分比
        List<Map<String, Object>> formattedStats = new ArrayList<>();
        for (Map<String, Object> stat : provinceStats) {
            String province = (String) stat.get("province");
            Long count = (Long) stat.get("count");

            Map<String, Object> formattedStat = new HashMap<>();
            formattedStat.put("province", province != null ? province : "未知");  // 修改：将"其他"改为"未知"
            formattedStat.put("softwareCount", count);

            // 计算百分比（保留1位小数）
            double percentage = totalSoftwareCount > 0 ?
                    (count.doubleValue() / totalSoftwareCount) * 100 : 0.0;
            formattedStat.put("softwarePercentage", Math.round(percentage * 10.0) / 10.0);

            formattedStats.add(formattedStat);
        }

        result.put("softwareProvinceStats", formattedStats);
        log.info("软件资产省份统计完成 - 总数: {}, 省份数量: {}", totalSoftwareCount, formattedStats.size());

        return result;
    }

    /**
     * 获取指定省份软件资产的资产分类细分
     * 作用：统计指定省份下各软件资产分类的数量和占比，确保返回完整的固定分类列表

     * 核心业务逻辑：
     * 1. 查询该省份软件资产总数
     * 2. 查询该省份各分类的实际统计数据
     * 3. 初始化所有软件资产固定分类映射表
     * 4. 创建包含所有固定分类的统计结果，默认数量为0
     * 5. 用实际查询结果更新对应分类的数量
     * 6. 计算各分类在该省份中的占比
     * 7. 返回完整的分类细分统计结果

     * 技术特点：
     * - 使用LinkedHashMap保持16个软件资产分类的顺序一致
     * - 确保返回所有固定分类，即使某些分类在该省份没有资产
     * - 基于该省份软件资产总数计算百分比，确保数据准确性
     * - 忽略数据库中不在固定映射表中的分类编码（理论上不应该存在）

     * 数据流程：
     * 数据库分类编码 → 固定分类映射 → 中文分类名称 → 完整分类列表
     *
     * @param province 省份名称，如"广东省"、"北京市"等
     * @return 包含省份、资产类型、总数和完整分类统计的Map对象

     * 返回数据结构：
     * {
     *   "province": "广东省",
     *   "assetType": "software",
     *   "totalCount": 100,
     *   "categoryStats": [
     *     {
     *       "categoryName": "操作系统",
     *       "count": 20,
     *       "percentage": 20.0
     *     },
     *     // ... 其他15个分类，包括数量为0的分类
     *   ]
     * }
     */
    @Override
    public Map<String, Object> getProvinceAssetCategoryDetail(String province) {
        log.info("开始统计省份软件资产分类细分 - 省份: {}", province);

        Map<String, Object> result = new HashMap<>();
        result.put("province", province);
        result.put("assetType", "software");

        // 1. 获取该省份软件资产总数
        Long provinceTotalCount = baseMapper.selectSoftwareCountByProvince(province);
        if (provinceTotalCount == null) provinceTotalCount = 0L;
        result.put("totalCount", provinceTotalCount);

        // 2. 获取该省份各资产分类的实际统计数据
        List<Map<String, Object>> categoryStats = baseMapper.selectSoftwareCategoryStatsByProvince(province);

        // 3. 定义所有软件资产分类的固定列表（使用分类名称作为标识）
        List<String> allCategoryNames = Arrays.asList(
                "操作系统", "数据库系统", "中间件", "软件开发环境",
                "网络通信软件", "文档处理软件", "图形图像软件", "数据处理软件",
                "模型算法软件", "地理信息系统", "移动应用软件", "安全防护软件",
                "设备管理软件", "作战指挥软件", "业务管理软件", "日常办公软件"
        );

        // 4. 创建分类统计映射，初始化所有分类数量为0
        Map<String, Long> categoryCountMap = new LinkedHashMap<>(); // 使用LinkedHashMap保持顺序
        for (String categoryName : allCategoryNames) {
            categoryCountMap.put(categoryName, 0L);
        }

        // 5. 填充实际统计数据
        for (Map<String, Object> stat : categoryStats) {
            String categoryName = (String) stat.get("asset_category"); // 直接获取分类名称
            Long count = (Long) stat.get("count");

            log.debug("处理分类统计 - 分类名称: {}, 数量: {}", categoryName, count);

            if (categoryName != null && categoryCountMap.containsKey(categoryName)) {
                categoryCountMap.put(categoryName, count);
                log.debug("成功更新分类统计 - 分类: {}, 数量: {}", categoryName, count);
            } else {
                log.warn("未知的分类名称: {}，已忽略", categoryName);
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
        log.info("软件资产分类细分统计完成 - 省份: {}, 总数: {}, 分类数: {}",
                province, provinceTotalCount, formattedStats.size());

        return result;
    }

    /**
     * 根据资产分类按省份统计软件资产数量

     * 核心逻辑：
     * 1. 软件资产表本身没有省份字段，需要通过关联report_unit表获取省份信息
     * 2. 统计指定资产分类下各省份的资产数量分布
     * 3. 处理省份为空的情况，统一归类为"未知"省份

     * 适用场景：
     * - 前端需要了解某类软件资产在全国各省份的分布情况
     * - 领导决策支持，分析软件资产的区域分布特征
     *
     * @param assetCategory 资产分类名称，必须是有效的分类（如"作战指挥软件"、"安全防护软件"等）
     * @return Map<String, Long> 省份-数量映射，key为省份名称，value为该省份的资产数量
     * @throws RuntimeException 当统计过程中发生数据库异常或其他系统异常时抛出

     * 示例返回：
     * {
     *   "北京市": 15,
     *   "广东省": 8,
     *   "江苏省": 6,
     *   "未知": 3
     * }
     */
    @Override
    public Map<String, Long> getProvinceStatsByAssetCategory(String assetCategory) {
        try {
            log.info("开始按资产分类统计软件资产省份分布 - assetCategory: {}", assetCategory);

            // 参数校验
            if (assetCategory == null || assetCategory.trim().isEmpty()) {
                log.warn("资产分类参数为空，无法进行统计");
                return Collections.emptyMap();
            }

            // 通过关联report_unit表获取省份信息进行统计
            List<Map<String, Object>> stats = softwareAssetMapper.selectProvinceStatsByAssetCategory(assetCategory);

            Map<String, Long> result = new HashMap<>();
            for (Map<String, Object> stat : stats) {
                String province = (String) stat.get("province");
                Long count = (Long) stat.get("count");

                // 处理省份为null或空字符串的情况，统一转为"未知"
                // 考虑因素：软件资产表没有省份字段，依赖report_unit表，可能存在关联失败的情况
                if (province == null || province.trim().isEmpty()) {
                    province = "未知";
                }
                result.put(province, count);
            }

            log.info("按资产分类统计软件资产省份分布完成 - assetCategory: {}, 统计省份数: {}",
                    assetCategory, result.size());
            return result;
        } catch (Exception e) {
            log.error("按资产分类统计软件资产省份分布失败 - assetCategory: {}", assetCategory, e);
            throw new RuntimeException("统计失败：" + e.getMessage());
        }
    }
}